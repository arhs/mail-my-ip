package com.spikeseed.arhs.corporate.tools.ipmailer;

import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Properties;

/**
 * The Class IPMailer.
 */
@Component
@SuppressWarnings("unused")
public class IPMailer {
    /** The Constant LOGGER. */
    private static final Logger LOGGER = LoggerFactory.getLogger(IPMailer.class);
    /** The subject part to search for to detect email. */
    private static final String IP_MAILER_GIVE_ME = "IP Mailer give me";
    /** Date format for email entete file like 2012-10-01 03:03:11. */
    private final SimpleDateFormat enteteDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /** The mail properties. */
    @Autowired
    MailProperties mailProperties;
    /** The JavaMailSender to send email. */
    @Autowired
    JavaMailSender mailSender;
    /** The overall properties. */
    Properties properties = new Properties();
    /** The specific properties. */
    @Autowired
    IPMailerProperties IPMailerProperties;
    /** The session. */
    private Session session;
    /** The hostname. */
    private String hostname;

    /** The mail part comparator. */
    @PostConstruct
    public void init() {
        /** Add the mail properties to the overall properties. */
        mailProperties.getProperties().entrySet().stream().forEach(e -> properties.put(e.getKey(), e.getValue()));
    }

    /** Run.
     * <br/>Execute one by minute. */
    @Scheduled(cron = "${application.ipmailer.cronExpression:0 * * * * ?}")
    public void run() {
        Store store = null;
        // Global
        Folder incomingGlobalFolder = null;
        Folder archiveGlobalFolder = null;
        // IP mailer
        Folder incomingIPMailerFolder = null;
        Folder archiveIPMailerFolder = null;
        LOGGER.info(getIPAdresses());
        if (this.hostname == null) {
            try {
                getHostname();
                LOGGER.info("Hostname is |" + this.hostname + "|");
            } catch (final IOException | InterruptedException e) {
                LOGGER.error("Error while getting hostname", e);
            }
        }
        try {
            store = getStore();
            logMailBoxStructure(store);
            // Root folders
            incomingGlobalFolder = store.getFolder(this.IPMailerProperties.getFolderGlobalInput());
            incomingGlobalFolder.open(Folder.READ_WRITE);
            // cleanup inbox -- !!!! only active on dev issue !!!
            //            for (final Folder tempfolderFolder : incomingGlobalFolder.list()) {
            //                tempfolderFolder.delete(true);
            //            }
            logMailBoxStructure(store);
            archiveGlobalFolder = store.getFolder(this.IPMailerProperties.getFolderGlobalArchive());
            archiveGlobalFolder.open(Folder.READ_WRITE);
            // IP Mailers folders
            // -> IP Mailer inbox
            createFolder(incomingGlobalFolder, this.IPMailerProperties.getFolderDedicatedInput());
            incomingIPMailerFolder = incomingGlobalFolder.getFolder(this.IPMailerProperties.getFolderDedicatedInput());
            incomingIPMailerFolder.open(Folder.READ_WRITE);
            // -> IP Mailer archive
            createFolder(incomingGlobalFolder, this.IPMailerProperties.getFolderDedicatedArchive());
            archiveIPMailerFolder = incomingGlobalFolder.getFolder(this.IPMailerProperties.getFolderDedicatedArchive());
            archiveIPMailerFolder.open(Folder.READ_WRITE);
            // Done creating and opening
            logMailBoxStructure(store);
            // Do the job
            handleGlobalFolder(incomingGlobalFolder, archiveGlobalFolder, incomingIPMailerFolder);
            handleIPMailerFolder(incomingIPMailerFolder, archiveIPMailerFolder);
        } catch (final Exception e) {
            final StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            LOGGER.error("General exception while connecting to the server " + "because of the following reason  " + e.getMessage() + "/r/n" + errors.toString());
        } finally {
            // global
            closeQuietly(incomingGlobalFolder);
            closeQuietly(archiveGlobalFolder);
            // IP Mailer
            closeQuietly(incomingIPMailerFolder);
            closeQuietly(archiveIPMailerFolder);
            try {
                if (store != null) {
                    store.close();
                }
            } catch (final Exception e) {
                // ignore
            }
        }
    }

    /**
     * Get email from Global inbox folder then:
     * <ul>
     * <li>Subject contains : IP mailer give me -> move to ipmailer inbox</li>
     * <li>Subject doesn't contains -> move to Global archive</li>
     * </ul>
     * @param incomingGlobalFolder The global folder to search IP Mailer email for.
     * @param archiveGlobalFolder The global archive folder to store email not for the IP Mailer.
     * @param incomingIPMailerFolder The folder to move IP mailer email to.
     * @throws MessagingException When interacting with messages
     */
    private void handleGlobalFolder(final Folder incomingGlobalFolder, final Folder archiveGlobalFolder, final Folder incomingIPMailerFolder) throws MessagingException {
        long start = System.currentTimeMillis();
        final int totalMessageCount = incomingGlobalFolder.getMessageCount();
        final int messageCount = totalMessageCount > this.IPMailerProperties.getStrategyMaxEmailToPoll() ? this.IPMailerProperties.getStrategyMaxEmailToPoll() : totalMessageCount;
        final Message[] messages = incomingGlobalFolder.getMessages(1, messageCount);
        LOGGER.info("Email global fetching done in " + (System.currentTimeMillis() - start) + " ms, fetches " + messageCount + "/" + totalMessageCount);
        final FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.CONTENT_INFO);
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        fp.add("X-mailer");
        incomingGlobalFolder.fetch(messages, fp);
        // Display message.
        for (final Message message : messages) {
            //workaround for asynchronous move
            if (message.getFlags().contains(Flags.Flag.DELETED)) {
                continue;
            }
            try {
                start = System.currentTimeMillis();
                if (message.getSubject().contains(IP_MAILER_GIVE_ME)) {
                    // handle email -> move it to sub inbox
                    LOGGER.info("Found email for IP Mailer from |" + extractEmailAdressCommaSeparated(message.getFrom()) + "| with subject: " + message.getSubject());
                    LOGGER.info("Moving email '" + message.getSubject() + "' to " + incomingIPMailerFolder + " folder");
                    moveMessage(incomingGlobalFolder, incomingIPMailerFolder, message);
                    LOGGER.info("Done, took " + (System.currentTimeMillis() - start) + " ms/t for treating email '" + message.getSubject() + "'");
                } else {
                    // move it to archive
                    LOGGER.info("Moving email '" + message.getSubject() + "' to " + archiveGlobalFolder + " folder");
                    moveMessage(incomingGlobalFolder, archiveGlobalFolder, message);
                    LOGGER.info("Done, took " + (System.currentTimeMillis() - start) + " ms/t for treating email '" + message.getSubject() + "'");
                }
            } catch (final Exception e) {
                final String errorHeader = "Impossible to handle the following email, the email has been moved to the error folder:";
                logErrorNotificationEmail(message, e, errorHeader);
            }
        }
    }

    /**
     * Get email from  IP mailer inbox folder then:
     * <ul>
     * <li>Subject contains : IP mailer give me -> handle the request</li>
     * <li>Subject doesn't contains -> move to IP mailer archive</li>
     * </ul>
     * @param incomingIPMailerFolder The dedicated incoming folder for IP Mailer.
     * @param archiveIPMailerFolder The dedicated archive folder for IP Mailer.
     * @throws MessagingException When interacting with message and mailboxes.
     */
    private void handleIPMailerFolder(final Folder incomingIPMailerFolder, final Folder archiveIPMailerFolder) throws MessagingException {
        long start = System.currentTimeMillis();
        final int totalMessageCount = incomingIPMailerFolder.getMessageCount();
        final int messageCount = totalMessageCount > this.IPMailerProperties.getStrategyMaxEmailToPoll() ? this.IPMailerProperties.getStrategyMaxEmailToPoll() : totalMessageCount;
        final Message[] messages = incomingIPMailerFolder.getMessages(1, messageCount);
        LOGGER.info("Email IP Mailer fetching done in " + (System.currentTimeMillis() - start) + " ms, fetches " + messageCount + "/" + totalMessageCount);
        final FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.CONTENT_INFO);
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        fp.add("X-mailer");
        incomingIPMailerFolder.fetch(messages, fp);
        // Display message.
        for (final Message message : messages) {
            //workaround for asynchronous move
            if (message.getFlags().contains(Flags.Flag.DELETED)) {
                continue;
            }
            try {
                start = System.currentTimeMillis();
                if (message.getSubject().contains(IP_MAILER_GIVE_ME)) {
                    // handle email 
                    LOGGER.info("Found email in dedicated mailbox for IP Mailer from |" + extractEmailAdressCommaSeparated(message.getFrom()) + "| with subject: " + message.getSubject());
                    // check if email is for my host
                    if (message.getSubject().endsWith(" "+this.hostname)) {
                        LOGGER.info("Email is for my host");
                        // For me handle it
                        final StringBuilder msgBody = new StringBuilder("Your IPS are:\r\n");
                        msgBody.append(getIPAdresses());
                        try {
                            MimeMessage msg = mailSender.createMimeMessage();
                            msg.setFrom(new InternetAddress(this.IPMailerProperties.getServerUser(), "IP mailer from " + this.hostname));
                            msg.addRecipient(Message.RecipientType.TO, message.getFrom()[0]);
                            msg.setSubject("RE: " + message.getSubject());
                            msg.setText(msgBody.toString());
                            mailSender.send(msg);
                            //                            Transport.send(msg);
                        } catch (final MessagingException e) {
                            LOGGER.error("Error while sending email", e);
                            continue;
                        }
                        final StringWriter stringWriter = new StringWriter();
                        //saveParts(message, stringWriter, false);
                        final String result = stringWriter.toString();
                        stringWriter.close();
                        LOGGER.info(result);
                        // delete it
                        LOGGER.warn("Deleting handled email '" + message.getSubject() + " from dedicated folder.");
                        deleteMessage(incomingIPMailerFolder, message);
                        LOGGER.warn("Done, took " + (System.currentTimeMillis() - start) + " ms/t for treating handled email '" + message.getSubject() + "'");
                    } else {
                        LOGGER.info("IP Mailer message is not for my host from |" + extractEmailAdressCommaSeparated(message.getFrom()) + "| with subject:" + message.getSubject());
                        // avoid it unless older than one hour
                        if ((System.currentTimeMillis() - message.getSentDate().getTime()) >= 60*60*1000) {
                            // move to archive
                            LOGGER.warn("Moving email '" + message.getSubject() + "' to " + archiveIPMailerFolder + " folder as it too old");
                            moveMessage(incomingIPMailerFolder, archiveIPMailerFolder, message);
                            LOGGER.warn("Done, took " + (System.currentTimeMillis() - start) + " ms/t for treating too old email '" + message.getSubject() + "'");
                        } else {
                            // just avoid it
                            LOGGER.info("IP Mailer message is not for my host from |" + extractEmailAdressCommaSeparated(message.getFrom()) + "| was avoided with subject:" + message.getSubject());
                        }
                    }
                } else {
                    // move it to archive
                    LOGGER.warn("Moving email '" + message.getSubject() + "' to " + archiveIPMailerFolder + " folder as it should not be there");
                    moveMessage(incomingIPMailerFolder, archiveIPMailerFolder, message);
                    LOGGER.warn("Done, took " + (System.currentTimeMillis() - start) + " ms/t for treating unexpected email '" + message.getSubject() + "'");
                }
            } catch (final Exception e) {
                final String errorHeader = "Impossible to handle the following email, the email has been moved to the error folder:";
                logErrorNotificationEmail(message, e, errorHeader);
            }
        }
    }

    /**
     * Get the hostname.
     * @throws IOException When reading the process output
     * @throws InterruptedException When executing the sub-process.
     */
    private void getHostname() throws IOException, InterruptedException {
        // Use a ProcessBuilder
        final ProcessBuilder pb = new ProcessBuilder("hostname");
        final Process p = pb.start();
        final InputStream is = p.getInputStream();
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        final StringBuilder tempOuput = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            tempOuput.append(line);
        }
        this.hostname = tempOuput.toString().trim();
        final int r = p.waitFor(); // Let the process finish.
        if (r != 0) { // error occured
            LOGGER.error("Hostname discovery exit with code |" + String.valueOf(r) + "| and output |" + this.hostname + "|");
            System.exit(r);
        }
    }

    /**
     * Log error notification email.
     * @param message The message which cause the exception
     * @param exception The exception cause
     * @param errorHeader The error header
     * @throws MessagingException The messaging exception
     */
    private void logErrorNotificationEmail(final Message message, final Exception exception, final String errorHeader) throws MessagingException {
        final StringWriter errors = new StringWriter();
        exception.printStackTrace(new PrintWriter(errors));
        /*  Email Information
                MailBox : a@b.c
                From : x@y.z
                Subject : subect
                Sent : 2015-10-14 01:02:03
                Received : 2015-10-1 07:08:09
         */
        final String errorMsg = errorHeader + " /r/n" + "Email information/r/n" + "MailBox  : " + this.IPMailerProperties.getServerUser() + "/r/n" + "From     : " + extractEmailAdressCommaSeparated(message.getFrom()) + "/r/n" + "To       : " + extractEmailAdressCommaSeparated(message.getRecipients(RecipientType.TO)) + "/r/n" + "Cc       : " + extractEmailAdressCommaSeparated(message.getRecipients(RecipientType.CC)) + "/r/n" + "Subject  : " + message.getSubject() + "/r/n" + "Sent     : " + this.enteteDateFormat.format(message.getSentDate()) + "/r/n" + "Received : " + this.enteteDateFormat.format(message.getReceivedDate()) + "/r/n/r/n" + "because of the following reason  " + exception.getMessage() + "/r/n" + errors.toString();
        LOGGER.error(errorMsg);
    }

    /**
     * Close quietly.
     * @param folder the folder
     */
    public void closeQuietly(final Folder folder) {
        try {
            if (folder != null) {
                folder.close(true);
            }
        } catch (final Exception e) {
            // ignore
        }
    }

    /**
     * Gets the store.
     * @return the store
     */
    private Store getStore() {
        try {
            if (this.session == null) {
                //allow to read the current property file (in case something goes wrong)
                if (this.IPMailerProperties.isServerSslEnabled()) {
                    final MailSSLSocketFactory sf = new MailSSLSocketFactory();
                    sf.setTrustAllHosts(true);
                    this.properties.put("mail.imaps.ssl.socketFactory", sf);
                }
                this.session = Session.getDefaultInstance(this.properties, null);
                this.session.setDebug(false);
            }
            final Store store = this.session.getStore(this.IPMailerProperties.isServerSslEnabled() ? "imaps" : "imap");
            store.connect(this.IPMailerProperties.getServerHost(), this.IPMailerProperties.getServerUser(), this.IPMailerProperties.getServerPassword());
            return store;
        } catch (final Exception e) {
            throw new RuntimeException("Impossible to create connection to server", e);
        }
    }

    /**
     * Extract the list of email adresses as a comma-space separated value list.
     * @param addresses Array of adresses
     * @return List of email like "adress1, adress2, adress3"
     */
    private static String extractEmailAdressCommaSeparated(final Address[] addresses) {
        if ((addresses == null) || (addresses.length == 0)) {
            return "";
        } else {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(((InternetAddress) addresses[0]).getAddress());
            for (int i = 1; i < addresses.length; i++) {
                stringBuilder.append(", ").append(((InternetAddress) addresses[i]).getAddress());
            }
            return stringBuilder.toString();
        }
    }

    /**
     * Create IMAP folder
     * @param parent The parent of the folder
     * @param folderName The name of the folder to create
     * @return True when folder was created
     */
    private boolean createFolder(final Folder parent, final String folderName) {
        LOGGER.info("Going to create folder |" + folderName + "| under parent |" + parent.getFullName() + "|");
        boolean isCreated;
        try {
            final Folder newFolder = parent.getFolder(folderName);
            isCreated = newFolder.create(Folder.HOLDS_MESSAGES);
            LOGGER.info("created: " + isCreated);
            if (newFolder.exists()) {
                LOGGER.info("really exist: " + folderName);
            } else {
                LOGGER.info("don't really exist: " + folderName);
            }
        } catch (final Exception e) {
            LOGGER.info("Error creating folder: ", e);
            isCreated = false;
        }
        return isCreated;
    }

    /**
     * Get the active IP adresses of the host.
     * @return The list of active network card with their associated IPs
     */
    private String getIPAdresses() {
        final StringBuilder ips = new StringBuilder();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface iface = interfaces.nextElement();
                // filters out 127.0.0.1 and inactive interfaces
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                ips.append("\r\n");
                final Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress addr = addresses.nextElement();
                    ips.append(iface.getDisplayName()).append(": ");
                    ips.append(addr.getHostAddress()).append("\r\n");
                }
            }
        } catch (final SocketException e) {
            throw new RuntimeException(e);
        }
        return ips.toString();
    }

    /**
     * Log the mailbox structure.
     * <br/>For debugging purpose.
     * @param store The store to log structure
     * @throws MessagingException When exploring structure.
     */
    private void logMailBoxStructure(final Store store) throws MessagingException {
        if (LOGGER.isInfoEnabled()) {
            final Folder defautlFolder = store.getDefaultFolder();
            LOGGER.info("defautlFolder.getFullName() |" + defautlFolder.getFullName() + "|");
            LOGGER.info("defautlFolder.getName() |" + defautlFolder.getName() + "|");
            logMailoxFolderChildren(defautlFolder, ">>");
        }
    }

    /**
     * Log the sub-mailbox structure.
     * @param folder The child folder.
     * @param prefix The prefix to use to log the children
     * @throws MessagingException When exploring structure.
     */
    private void logMailoxFolderChildren(final Folder folder, final String prefix) throws MessagingException {
        final Folder[] f = folder.list();
        for (final Folder childFolder : f) {
            LOGGER.info(prefix + childFolder.getName());
            logMailoxFolderChildren(childFolder, prefix + ">");
        }
    }

    /**
     * Move a message from a folder to another.
     * @param sourceFolder Source folder of the message.
     * @param destFolder Destination folder of the message
     * @param messageToDelete Message to move.
     * @throws MessagingException When exception occurs while moving
     */
    private void moveMessage(final Folder sourceFolder, final Folder destFolder, final Message messageToDelete) throws MessagingException {
        messageToDelete.setFlag(Flags.Flag.SEEN, true);
        final Message[] tempArray = new Message[]{messageToDelete};
        sourceFolder.copyMessages(tempArray, destFolder);
        sourceFolder.setFlags(tempArray, new Flags(Flags.Flag.DELETED), true);
    }

    /**
     * Delete a message from a folder.
     * @param sourceFolder Source folder of the message.
     * @param messageToDelete Message to delete.
     * @throws MessagingException When exception occurs while moving
     */
    private void deleteMessage(final Folder sourceFolder, final Message messageToDelete) throws MessagingException {
        messageToDelete.setFlag(Flags.Flag.SEEN, true);
        final Message[] tempArray = new Message[]{messageToDelete};
        sourceFolder.setFlags(tempArray, new Flags(Flags.Flag.DELETED), true);
    }
}
