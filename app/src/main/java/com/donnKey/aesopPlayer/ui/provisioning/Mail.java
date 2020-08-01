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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.sun.mail.imap.IMAPBodyPart;
import com.sun.mail.imap.IMAPStore;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;

import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SubjectTerm;

public class Mail {
    @Inject
    public GlobalSettings globalSettings;

    private String login;
    private String password;
    private final ArrayList<String> recipients = new ArrayList<>();
    private String subject = null;
    private String messageBody = null;
    private String inboundFrom = null;

    private final String SMTPHostname;
    private final String IMAPHostname;

    private long timestamp;
    private BufferedReader contentStream;

    public Mail(@NonNull AppCompatActivity activity) {
        Context appContext = activity.getApplicationContext();
        AesopPlayerApplication.getComponent(appContext).inject(this);
        String baseHostname = globalSettings.getMailHostname();
        SMTPHostname = "smtp." + baseHostname;
        IMAPHostname = "imap." + baseHostname;

        login = globalSettings.getMailLogin();
        password = globalSettings.getMailPassword();
    }

    public boolean readMail(String desiredSubject)
    {
        boolean result = false;
        inboundFrom = null;

        try {
            Properties props = new Properties();
            props.put("mail.imaps.host", IMAPHostname);
            Session receiverSession = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(login, password);
                    }
                });

            // Get the store
            IMAPStore imapStore = (IMAPStore) receiverSession.getStore("imaps");
            imapStore.connect(login, password);

            // And the default folder
            Folder inbox = imapStore.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Get any candidate messages: the newest one with the appropriate subject line
            Calendar today = Calendar.getInstance();
            today.roll(Calendar.DAY_OF_YEAR, false);
            //?????????????????????? delete below
            today.roll(Calendar.DAY_OF_YEAR, false);
            today.roll(Calendar.DAY_OF_YEAR, false);

            ReceivedDateTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GT, today.getTime());
            SubjectTerm subjectTerm = new SubjectTerm(desiredSubject);
            AndTerm andTerm = new AndTerm(dateTerm, subjectTerm);
            /* ????????????
            FromTerm fromTerm = new FromTerm(new InternetAddress(inboundFrom));
            andTerm = new AndTerm(andTerm, fromTerm);
             */
            Message[] messages = inbox.search(andTerm);

            if (messages.length > 0) {
                findMessage:
                // Temporarily taking last ??????????????????????????????????????
                for (int i = messages.length-1; i >= 0; i--)
                //for (int i = 0; i<messages.length; i++)
                {
                    Message message = messages[i];
                    //??????????? Should do this at some point
                    //message.setFlag(Flags.Flag.DELETED, true);
                    Object content = message.getContent();
                    if (content instanceof MimeMultipart) {
                        MimeMultipart mmp = (MimeMultipart) content;
                        int count = mmp.getCount();
                        for (int j = 0; j < count; j++) {
                            BodyPart mimePart = mmp.getBodyPart(j);
                            if (mimePart instanceof IMAPBodyPart
                                    && mimePart.getContentType().contains("TEXT/PLAIN")) {
                                IMAPBodyPart iMimePart = (IMAPBodyPart) mimePart;
                                @SuppressWarnings("CharsetObjectCanBeUsed") InputStream cs = new ByteArrayInputStream(
                                        ((String) (iMimePart.getContent())).getBytes("UTF-8"));
                                contentStream = new BufferedReader(new InputStreamReader(cs));
                                timestamp = message.getReceivedDate().getTime();
                                Address[] messageFromNames = message.getFrom();
                                if (messageFromNames != null && messageFromNames.length > 0) {
                                    inboundFrom = messageFromNames[0].toString();
                                    Log.w("AESOP " + getClass().getSimpleName(), "Mail is from " + inboundFrom);
                                }
                                result = true;
                                break findMessage;
                            }
                        }
                    }
                }
            }

            //?????????????????? should be:
            //inbox.close(true);
            inbox.close(false);
            imapStore.close();

        }
        catch (Exception e) {
            Log.w("AESOP " + getClass().getSimpleName(), "SEND CRASHED");
            CrashWrapper.recordException(e);
            e.printStackTrace();
        }

        return result;
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

            Log.w("AESOP " + getClass().getSimpleName(), "Mail sent");
        } catch (Exception e) {
            Log.w("AESOP " + getClass().getSimpleName(), "Mail failed");
            CrashWrapper.recordException(e);
            e.printStackTrace();
        }
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

    public long getInboundTimestamp() {
        return timestamp;
    }

    public BufferedReader getInboundBodyStream() {
        return contentStream;
    }

    public String getInboundSender() {
        return inboundFrom;
    }
}
