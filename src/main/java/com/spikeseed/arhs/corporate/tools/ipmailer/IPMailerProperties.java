package com.spikeseed.arhs.corporate.tools.ipmailer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The IP Mailer specific properties coming from files or -D override
 */
@ConfigurationProperties(prefix = "ipmailer", ignoreUnknownFields = false)
@Component
public class IPMailerProperties {
    /** The incoming global folder location. */
    private String folderGlobalInput;
    /** The archive global folder location. */
    private String folderGlobalArchive;
    /** The incoming IP Mailer folder location. */
    private String folderDedicatedInput;
    /** The archive IP Mailer folder location. */
    private String folderDedicatedArchive;
    /** The host. */
    private String serverHost;
    /** The user. */
    private String serverUser;
    /** The password. */
    private String serverPassword;
    /** The ssl enabled. */
    private boolean serverSslEnabled;
    /** The max email to poll. */
    private int strategyMaxEmailToPoll;

    public String getFolderGlobalInput() {
        return folderGlobalInput;
    }

    @SuppressWarnings("unused")
    public void setFolderGlobalInput(String folderGlobalInput) {
        this.folderGlobalInput = folderGlobalInput;
    }

    public String getFolderGlobalArchive() {
        return folderGlobalArchive;
    }

    @SuppressWarnings("unused")
    public void setFolderGlobalArchive(String folderGlobalArchive) {
        this.folderGlobalArchive = folderGlobalArchive;
    }

    public String getFolderDedicatedInput() {
        return folderDedicatedInput;
    }

    @SuppressWarnings("unused")
    public void setFolderDedicatedInput(String folderDedicatedInput) {
        this.folderDedicatedInput = folderDedicatedInput;
    }

    public String getFolderDedicatedArchive() {
        return folderDedicatedArchive;
    }

    @SuppressWarnings("unused")
    public void setFolderDedicatedArchive(String folderDedicatedArchive) {
        this.folderDedicatedArchive = folderDedicatedArchive;
    }

    public String getServerHost() {
        return serverHost;
    }

    @SuppressWarnings("unused")
    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public String getServerUser() {
        return serverUser;
    }

    @SuppressWarnings("unused")
    public void setServerUser(String serverUser) {
        this.serverUser = serverUser;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    @SuppressWarnings("unused")
    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    @SuppressWarnings("unused")
    public boolean isServerSslEnabled() {
        return serverSslEnabled;
    }

    @SuppressWarnings("unused")
    public void setServerSslEnabled(boolean serverSslEnabled) {
        this.serverSslEnabled = serverSslEnabled;
    }

    @SuppressWarnings("unused")
    public int getStrategyMaxEmailToPoll() {
        return strategyMaxEmailToPoll;
    }

    @SuppressWarnings("unused")
    public void setStrategyMaxEmailToPoll(int strategyMaxEmailToPoll) {
        this.strategyMaxEmailToPoll = strategyMaxEmailToPoll;
    }
}
