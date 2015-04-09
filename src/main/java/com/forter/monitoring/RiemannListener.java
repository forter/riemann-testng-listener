package com.forter.monitoring;

import com.forter.monitoring.utils.Discovery;

import com.aphyr.riemann.client.RiemannClient;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.events.RepositoryListener;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.io.*;

public class RiemannListener extends TestListenerAdapter{
    private String machineName;
    private RiemannClient client;
    private String commitHash = null;
    private boolean isAws;

    private void connect() {
        if (client == null) {
            try {
                machineName = Discovery.instance().getMachineName();
                String riemannIP = Discovery.instance().getRiemannIP(machineName);
                final int riemannPort = 5555;
                client = RiemannClient.tcp(riemannIP, riemannPort);
                client.connect();
            }
            catch (IOException e) {
                System.err.println("Cannot connect to riemann " + e.getMessage());
            }
        }
    }

    private void getGitHash() throws IOException {
        if (this.commitHash == null) {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();

            Repository repository = builder.findGitDir().readEnvironment().build();

            final ObjectId head = repository.resolve(Constants.HEAD);

            if (head == null) {
                throw new RuntimeException(String.format("Could not locate GIT repository, DIR: %s", new File("").getAbsolutePath()));
            }

            this.commitHash = head.getName();
        }
    }

    private void sendEvent(ITestResult tr, String state) {
        StringWriter errors = new StringWriter();
        String description;

        if (client != null && isAws) {
            if (state.equals("failed")) {
                tr.getThrowable().printStackTrace(new PrintWriter(errors));
                description = errors.toString();
            } else {
                description = null;
            }

            Preconditions.checkNotNull(commitHash, "Commit hash was null!");

            final int eventTTL = 20;
            client.event().
                    service(machineName + " " + tr.getInstanceName() + "-" + tr.getName()).
                    state(state).
                    tags("javatests").
                    description(description).
                    ttl(eventTTL).
                    attribute("commitHash", commitHash).
                    send();
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        this.isAws = Discovery.instance().isAWS();
        if (this.isAws) {
            try {
                getGitHash();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            connect();
        }
    }

    @Override
    public void onTestFailure(ITestResult tr) {
        sendEvent(tr, "failed");
    }

    @Override
    public void onConfigurationFailure(ITestResult tr) {
        sendEvent(tr, "failed");
    }

    @Override
    public void onConfigurationSkip(ITestResult tr) {
        sendEvent(tr, "skipped");
    }

    @Override
    public void onConfigurationSuccess(ITestResult tr) {
        sendEvent(tr, "passed");
    }

    @Override
    public void onTestSkipped(ITestResult tr) {
        sendEvent(tr, "skipped");
    }

    @Override
    public void onTestSuccess(ITestResult tr) {
        sendEvent(tr, "passed");
    }

    @Override
    public void onFinish(ITestContext testContext) {
        if (client != null) {
            try {
                client.disconnect();
            } catch (IOException e) {
                System.err.println("Cannot disconnect from riemann " + e.getMessage());
                throw Throwables.propagate(e);
            }
            client = null;
        }
    }
}
