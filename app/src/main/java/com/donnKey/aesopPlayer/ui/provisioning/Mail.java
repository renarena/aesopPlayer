/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.donnKey.aesopPlayer.ui.provisioning;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.sun.mail.imap.IMAPBodyPart;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.util.MailConnectException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Properties;

import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

public class Mail implements Iterable<Mail.Request>{
    @Inject
    public GlobalSettings globalSettings;

    static public final int SUCCESS = 0;
    static public final int UNRECOGNIZED_HOST = 1;
    static public final int UNRECOGNIZED_USER_PASSWORD = 2;
    static public final int OTHER_ERROR = 3;
    static public final int PENDING = 4;
    static public final int UNRESOLVED = 5;

    private String login;
    private String password;
    private final String deviceName;
    private final ArrayList<String> recipients = new ArrayList<>();
    private String subject = null;
    private String messageBody = null;
    private String inboundFrom = null;

    @SuppressWarnings("FieldCanBeLocal")
    private final String mailSubject = "Aesop request";

    private final String SMTPHostname;
    private final String IMAPHostname;

    IMAPStore imapStore = null;
    Message[] messages;
    Folder inbox;

    public Mail() {
        AesopPlayerApplication.getComponent().inject(this);
        String baseHostname = globalSettings.getMailHostname();
        SMTPHostname = "smtp." + baseHostname;
        IMAPHostname = "imap." + baseHostname;

        login = globalSettings.getMailLogin();
        password = globalSettings.getMailPassword();
        deviceName = globalSettings.getMailDeviceName();
    }

    public int open() {
        imapStore = null;
        int result;

        try {
            Properties props = new Properties();
            props.put("mail.imaps.host", IMAPHostname);
            props.put("mail.imaps.connectiontimeout", "5000");
            Session receiverSession = Session.getInstance(props,
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(login, password);
                        }
                    });

            // Get the store
            imapStore = (IMAPStore) receiverSession.getStore("imaps");
            imapStore.connect(login, password);
            result = SUCCESS;
        }
        catch (MailConnectException e) {
            // thrown for bad hostname by connect
            result = UNRECOGNIZED_HOST;
        } catch (MessagingException e) {
            // thrown for bad login/password by connect
            result = UNRECOGNIZED_USER_PASSWORD;
        } catch (Exception e) {
            CrashWrapper.recordException(e);
            result = OTHER_ERROR;
        }
        return result;
    }

    public int readMail() {
        inboundFrom = null;
        int result = SUCCESS;

        try {
            // Get the default folder
            inbox = imapStore.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Get any candidate messages: the newest one with the appropriate subject line
            Calendar today = Calendar.getInstance();
            today.add(Calendar.MONTH, -1);

            SearchTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GT, today.getTime());
            SearchTerm subjectTerm = new SubjectTerm(mailSubject);
            SearchTerm andTerm = new AndTerm(dateTerm, subjectTerm);
            if (!deviceName.isEmpty()) {
                subjectTerm = new SubjectTerm(deviceName);
                andTerm = new AndTerm(andTerm, subjectTerm);
            }
            /*
            If a problem develops with junk mail being interpreted, it would be possible
            to require that the sender be the registered mail user, per the below.
            if (!fromName.isEmpty()) {
                FromTerm fromTerm = new FromTerm(new InternetAddress(inboundFrom));
                andTerm = new AndTerm(andTerm, fromTerm);
            }
             */
            messages = inbox.search(andTerm);
        } catch (Exception e) {
            result = OTHER_ERROR;
            CrashWrapper.recordException(e);
        }

        return result;
    }

    public class Request {
        final Message message;
        Request(Message message) {
            this.message = message;
        }

        public Date sentTime() {
            Date timestamp = null;
            try {
                timestamp = message.getReceivedDate();
            } catch (MessagingException e) {
                CrashWrapper.recordException(e);
            }
            return timestamp;
        }

        public BufferedReader getMessageBodyStream() {
            try {
                Object content = message.getContent();
                if (content instanceof MimeMultipart) {
                    MimeMultipart mmp = (MimeMultipart) content;
                    int count = mmp.getCount();
                    for (int j = 0; j < count; j++) {
                        BodyPart mimePart = mmp.getBodyPart(j);
                        if (mimePart instanceof IMAPBodyPart
                                && mimePart.getContentType().contains("TEXT/PLAIN")) {
                            IMAPBodyPart iMimePart = (IMAPBodyPart) mimePart;
                            @SuppressWarnings("CharsetObjectCanBeUsed")
                            InputStream cs = new ByteArrayInputStream(
                                ((String) (iMimePart.getContent())).getBytes("UTF-8"));
                            return new BufferedReader(new InputStreamReader(cs));
                        }
                    }
                }
            } catch (IOException e) {
                CrashWrapper.recordException(e);
            } catch (MessagingException e) {
                CrashWrapper.recordException(e);
            }
            return null;
        }

        public String getMessageSender() {
            Address[] messageFromNames = new Address[0];
            try {
                messageFromNames = message.getFrom();
            } catch (MessagingException e) {
                CrashWrapper.recordException(e);
            }
            if (messageFromNames != null && messageFromNames.length > 0) {
                inboundFrom = messageFromNames[0].toString();
                return inboundFrom;
            }
            return null;
        }

        public void delete()
        {
            try {
                message.setFlag(Flags.Flag.DELETED, true);
            } catch (MessagingException e) {
                CrashWrapper.recordException(e);
            }
        }
    }

    @NonNull
    @Override
    public Iterator<Request> iterator() {
        return new Iterator<Request>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < messages.length;
            }

            @Override
            public Request next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                return new Request(messages[index++]);
            }
        };
    }

    public void close() {
        try {
            if (inbox != null) {
                inbox.close(true);
            }
            if (imapStore != null) {
                imapStore.close();
            }
        } catch (Exception e) {
            CrashWrapper.recordException(e);
        }
    }

    public void sendEmail() {
        Properties props = new Properties();

        // This is for gmail (and likely others)
        props.put("mail.smtp.host", SMTPHostname);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");
        //props.put("mail.debug", "true"); // just in case

        Session session = Session.getInstance(props,
            new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(login, password);
                }
            });

        try {
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(login));
            if (recipients.size() <= 0) {
                throw new Exception("Provisioning mail: sending to no recipients");
            }
            for (String r:recipients) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(r));
            }
            message.setSubject(subject);
            message.setText(messageBody);

            Transport.send(message);

        } catch (Exception e) {
            CrashWrapper.recordException(e);
        }
    }

    public int testConnection() {
        int result = open();
        close();
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Mail setRecipient(String recipient) {
        this.recipients.add(recipient);
        return this;
    }

    public Mail setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Mail setMessageBody(String messageBody) {
        this.messageBody = messageBody;
        return this;
    }

    public Mail setPassword(String password) {
        this.password = password;
        return this;
    }

    public Mail setLogin(String login) {
        this.login = login;
        return this;
    }

}
