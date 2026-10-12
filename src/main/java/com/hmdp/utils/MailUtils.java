package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Email verification code utility. The sender mailbox is read from
 * hmdp.mail in application.yaml instead of hardcoding credentials.
 * Callers must tolerate it being unconfigured (the code is already
 * in Redis and can be read from the log or Redis).
 */
@Component
public class MailUtils {

    private static String mailUser;
    private static String mailPassword;

    @Value("${hmdp.mail.user:}")
    public void setMailUser(String mailUser) {
        MailUtils.mailUser = mailUser;
    }

    @Value("${hmdp.mail.password:}")
    public void setMailPassword(String mailPassword) {
        MailUtils.mailPassword = mailPassword;
    }

    public static void main(String[] args) throws MessagingException {
        // Test the method directly here with your own email (configure hmdp.mail in the yaml first)
        sendtoMail("test@example.com", new MailUtils().achieveCode());
    }

    // Email sending logic
    public static void sendtoMail(String email, String code) throws MessagingException {
        if (mailUser == null || mailUser.isEmpty() || mailPassword == null || mailPassword.isEmpty()) {
            throw new MessagingException("Sender mailbox not configured: set hmdp.mail.user / hmdp.mail.password in application.yaml");
        }
        // Properties holding the mail settings
        Properties props = new Properties();
        // SMTP requires authentication
        props.put("mail.smtp.auth", "true");
        // SMTP server
        props.put("mail.smtp.host", "smtp.qq.com");
        // Port; 587 for QQ Mail
        props.put("mail.smtp.port", "587");
        // Sender account
        props.put("mail.user", mailUser);
        // 16-char SMTP token
        props.put("mail.password", mailPassword);
        // Build the auth credentials for SMTP
        Authenticator authenticator = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                // username + password
                String userName = props.getProperty("mail.user");
                String password = props.getProperty("mail.password");
                return new PasswordAuthentication(userName, password);
            }
        };
        // Create the mail session from the properties and credentials
        Session mailSession = Session.getInstance(props, authenticator);
        // Create the message
        MimeMessage message = new MimeMessage(mailSession);
        // Set the sender
        InternetAddress form = new InternetAddress(props.getProperty("mail.user"));
        message.setFrom(form);
        // Set the recipient
        InternetAddress to = new InternetAddress(email);
        message.setRecipient(MimeMessage.RecipientType.TO, to);
        // Set the subject
        message.setSubject("Verification code");
        // Set the body
        message.setContent("Hello,\nYour verification code is: " + code + " (valid for 1 minute, do not share it)", "text/html;charset=UTF-8");
        // And finally, send it
        Transport.send(message);
    }

    // Code generation logic
    public static String achieveCode() {  // digits 1/0 look like letters O/l, so they are excluded
        String[] beforeShuffle = new String[]{"2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F",
                "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "a",
                "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                "w", "x", "y", "z"};
        List<String> list = Arrays.asList(beforeShuffle); // String -> List
        Collections.shuffle(list);  // shuffle needs a List, hence the conversion
        StringBuilder sb = new StringBuilder();
        for (String s : beforeShuffle) {
            sb.append(s); // back to a String
        }
        return sb.substring(3, 8);
    }
}
