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
import androidx.core.text.HtmlCompat;

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

public class Mail implements Iterable<Mail.Request> {
    @Inject
    public GlobalSettings globalSettings;

    static public final int SUCCESS = 0;
    static public final int UNRECOGNIZED_HOST = 1;
    static public final int UNRECOGNIZED_USER_PASSWORD = 2;
    static public final int OTHER_ERROR = 3;
    static public final int PENDING = 4;
    static public final int UNRESOLVED = 5;
    static public final int NOT_SEEN = 6;
    static public final int HAS_SEEN = 7;
    static public final int MISSING_DEVICE_NAME = 8;
    static public final int MISSING_TAGS = 9;

    static public final String TAG = "MAIL";

    private String login;
    private String password;
    private final String deviceName;
    private final ArrayList<String> recipients = new ArrayList<>();
    private String subject = null;
    private String messageBody = null;
    private String inboundFrom = null;

    private final String mailSubject = "Aesop request";

    private final String SMTPHostname;
    private final String IMAPHostname;

    IMAPStore imapStore = null;
    Message[] messages;
    Folder inbox;
    Flags inboxFlags;
    boolean flagsSupported;

    public Mail() {
        AesopPlayerApplication.getComponent().inject(this);
        String baseHostname = globalSettings.getMailHostname();
        SMTPHostname = "smtp." + baseHostname;
        IMAPHostname = "imap." + baseHostname;

        login = globalSettings.getMailLogin();
        password = globalSettings.getMailPassword();
        deviceName = globalSettings.getMailDeviceName().toLowerCase();
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
            inbox = imapStore.getFolder("INBOX");

            // Get the default folder
            inbox.open(Folder.READ_WRITE);

            inboxFlags = inbox.getPermanentFlags();
            flagsSupported = false;
            if (inboxFlags != null) {
                flagsSupported = inboxFlags.contains(Flags.Flag.USER);
            }

            result = SUCCESS;
        } catch (MailConnectException e) {
            // thrown for bad hostname by connect
            result = UNRECOGNIZED_HOST;
        } catch (MessagingException e) {
            // thrown for bad login/password by connect
            result = UNRECOGNIZED_USER_PASSWORD;
        } catch (Exception e) {
            CrashWrapper.recordException(TAG, e);
            result = OTHER_ERROR;
        }
        return result;
    }

    public boolean userFlagsSupported() {
        return flagsSupported;
    }

    public int readMail() {
        inboundFrom = null;
        int result = SUCCESS;

        try {
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
            CrashWrapper.recordException(TAG, e);
        }

        return result;
    }

    public class Request {
        final Message message;
        String[] subjectTags;
        Flags messageFlags;
        Request(@NonNull Message message) {
            this.message = message;
            try {
                messageFlags = message.getFlags();
            } catch (MessagingException e) {
                messageFlags = new Flags();
            }
            try {
                String subj = message.getSubject();
                subj = subj.replaceFirst("(?i)" + mailSubject,"");
                subj = subj.trim();
                subj = subj.toLowerCase();
                if (subj.isEmpty()) {
                    subjectTags = new String[0];
                }
                else {
                    subjectTags = subj.split("\\s+");
                }
            } catch (MessagingException e) {
                subjectTags = new String[0];
            }
        }

        public Date receivedTime() {
            Date timestamp = null;
            try {
                timestamp = message.getReceivedDate();
            } catch (MessagingException e) {
                CrashWrapper.recordException(TAG, e);
            }
            return timestamp;
        }

        public BufferedReader getMessageBodyStream() {
            try {
                Object content = message.getContent();

                // If it's old-style plain text, that's what the sender wanted... use that.
                if (content instanceof String) {
                    //noinspection CharsetObjectCanBeUsed
                    InputStream cs = new ByteArrayInputStream(
                            ((String) (content)).getBytes("UTF-8"));
                    return new BufferedReader(new InputStreamReader(cs));
                }

                // If it's MIME, prefer html that we strip to plain text
                // If there's a TEXT/PLAIN, it likely has line folding that breaks things
                // because of the long URLs!
                if (content instanceof MimeMultipart) {
                    MimeMultipart mmp = (MimeMultipart) content;
                    int count = mmp.getCount();
                    // Look for HTML
                    for (int j = 0; j < count; j++) {
                        BodyPart mimePart = mmp.getBodyPart(j);
                        if (mimePart instanceof IMAPBodyPart
                                && mimePart.getContentType().contains("TEXT/HTML")) {
                            IMAPBodyPart iMimePart = (IMAPBodyPart) mimePart;
                            String flatText = HtmlCompat.fromHtml((String)iMimePart.getContent(),
                                HtmlCompat.FROM_HTML_MODE_COMPACT).toString();
                            @SuppressWarnings("CharsetObjectCanBeUsed")
                            InputStream cs = new ByteArrayInputStream(
                                flatText.getBytes("UTF-8"));
                            return new BufferedReader(new InputStreamReader(cs));
                        }
                    }
                    // Look for plain text
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
                CrashWrapper.recordException(TAG, e);
            } catch (MessagingException e) {
                CrashWrapper.recordException(TAG, e);
            }
            return null;
        }

        public int checkSeen() {
            if (deviceName.isEmpty()) {
                if (subjectTags.length == 0) {
                   return NOT_SEEN; // not previously seen
                }
                return MISSING_DEVICE_NAME;
            }
            else {
                if (subjectTags.length == 0) {
                    // This shouldn't ever happen because the mail filter would never pass
                    // a message without a matching tag
                    return MISSING_TAGS;
                }
            }
            return messageFlags.contains(deviceName)?HAS_SEEN:NOT_SEEN;
        }

        private boolean checkAllSeen() {
            // Have all other devices seen this (we have, but it will not yet be in tags[])
            if (deviceName.isEmpty()) {
                return true;
            }

            boolean allSeen = true;
            for (String s: subjectTags) {
                if (!s.equals(deviceName)) {
                    if (!messageFlags.contains(s)) {
                        allSeen = false;
                    }
                }
            }
            return allSeen;
        }

        public String getMessageSender() {
            Address[] messageFromNames = new Address[0];
            try {
                messageFromNames = message.getFrom();
            } catch (MessagingException e) {
                CrashWrapper.recordException(TAG, e);
            }
            if (messageFromNames != null && messageFromNames.length > 0) {
                inboundFrom = messageFromNames[0].toString();
                return inboundFrom;
            }
            return null;
        }

        public void delete()
        {
            if (!checkAllSeen()) {
                // Not all seen, add myself
                Flags fl = new Flags();
                fl.add(deviceName);
                try {
                    message.setFlags(fl, true);
                } catch (MessagingException e) {
                    CrashWrapper.recordException(TAG, e);
                }
                return;
            }

            // Everyone is done (we were the last), really delete it.
            try {
                message.setFlag(Flags.Flag.DELETED, true);
            } catch (MessagingException e) {
                CrashWrapper.recordException(TAG, e);
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
                // Close, expunging deleted messages
                inbox.close(true);
            }
            if (imapStore != null) {
                imapStore.close();
            }
        } catch (Exception e) {
            CrashWrapper.recordException(TAG, e);
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
            CrashWrapper.recordException(TAG, e);
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
