package com.forter.monitoring;

import com.forter.monitoring.utils.Discovery;

import com.aphyr.riemann.client.RiemannClient;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
                int riemannPort = 5555;
                client = RiemannClient.tcp(riemannIP, riemannPort);
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }

            try {
                // initializes client, connection is actually async
                client.connect();
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void getGitHash() {
        if (this.commitHash == null) {
            Process p;
            String command = "git rev-parse --verify HEAD";
            try {
                p = Runtime.getRuntime().exec(command);
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                this.commitHash = reader.readLine();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private void sendEvent(ITestResult tr, String state) {
        StringWriter errors = new StringWriter();
        String description;

        if (isAws) {
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
            getGitHash();
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
