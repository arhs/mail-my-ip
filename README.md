# IP Mailer

This tool checks a mailbox and search for email send form it. Send it send back the list of active interface with their associated IPs as answers.

## Behavior
The tool search for email containing in subject the part *IP Mailer give me * followed by the hostname on which the service is running in the **global inbox**:

- Matching email will be move to dedicated inbox folder.
- Non-matching emails will be move to **global archive** folder to prevent handling them again.

The tool will after search for emails in **dedicated inbox** :
> Please note that due to some IMAP lags while moving between folders, such email could only be handled on next execution.

- Matching email will be handled in the following way:
  - Email is for the host, an answer will be provided as attended. 
  - Email if not for the host, email will be avoided in order to allow other tools to handle it them-self.
  - Email is older than one hour, it will be moved to **dedicated archive** folder to prevent handling them again as they are unattended.
- Non-matching emails will be move to **dedicated archive** folder to prevent handling them again as they are unattended.

## Configuration
Configuration can either be done by changing the properties files or by using the *--* parameter on command line.

Configuration variables are:

* ipmailer.serverSslEnabled *(boolean)* Is IMAP server using SSL
* ipmailer.serverHost *(String)* The IMAP server hostname
* ipmailer.serverUser *(String)* The username of the IMAP account
* ipmailer.serverPassword *(String)* The password of the IMAP account
* ipmailer.folderGlobalInput *(String)* The global inbox folder to check for new request
* ipmailer.folderGlobalArchive *(String)* The global archive folder When an email is found in global inbox but not for this service, the email is moved here
* ipmailer.folderDedicatedInput *(String)* The dedicated inbox folder When an matching email was found in the global inbox, it is moved here for later processing
* ipmailer.folder-dedicated-archive *(String)* The dedicated archive folder When a email request has been handled, it is moved here
* ipmailer.strategyMaxEmailToPoll *(int)* Max number of email to handle on each iteration

Some additional configuration:

* spring.mail.imap.ssl.trust
* spring.mail.imap.starttls.enable=false
* spring.mail.imaps.partialfetch=false
* spring.mail.imap.partialfetch=false
* spring.mail.imaps.timeout=10000
* spring.mail.imap.timeout=10000
* spring.mail.imap.connectiontimeout=10000
* spring.mail.imaps.connectiontimeout=10000
* spring.mail.smtp.host *(String)* The SMTP server hostname
* spring.mail.host *(String)* The IMAP server hostname


## Run it as a Windows service
The easiest way to run it as a service is to use WinSW  from https://github.com/kohsuke/winsw

### Steps
1. Download WinSW [binaries](http://repo.jenkins-ci.org/releases/com/sun/winsw/winsw/)
2. Extract them to a new folder
3. Copy the IP mailer jar to this directory (like ip-mailer-1.0.0.jar)
4. Rename the winsw.exe to the name of our service (like ipmailer.exe)
5. Create the winSW XML configuration file (like ipmailer.xml)
```
<service>
  <id>ipmailer</id>
  <name>IP Mailer</name>
  <description>This service runs IP Mailer application</description>
  <executable>java</executable>
  <arguments>-Xrs -Xmx256m -jar "%BASE%\ip-mailer-1.0.0.jar" --ipmailer.serverHost=my-email-host ...</arguments>
  <logmode>rotate</logmode>
</service>
```
5. Execute the command to create the windows service
> ipmailer.exe install
