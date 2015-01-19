package com.forter.monitoring;

import com.forter.monitoring.utils.Discovery;

import com.aphyr.riemann.client.RiemannClient;
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
    private String riemannIP;
    private String machineName;
    private final int riemannPort = 5555;
    private final int eventTTL = 20;
    private RiemannClient client;
    private String commitHash = null;

    public void connect() {
        if (client == null) {
            try {
                machineName = Discovery.instance().getMachineName();
                riemannIP = Discovery.instance().getRiemannIP(machineName);
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

    public String getGitHash() {
        if (commitHash == null) {
            StringBuffer output = new StringBuffer();
            Process p;
            String command = "git rev-parse --verify HEAD";
            try {
                p = Runtime.getRuntime().exec(command);
                p.waitFor();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(p.getInputStream()));

                String line = "";
                while ((line = reader.readLine())!= null) {
                    output.append(line + "\n");
                }
                commitHash = output.toString();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return commitHash;
    }

    public void sendEvent(ITestResult tr, String state) {
        StringWriter errors = new StringWriter();
        String description;

        if (Discovery.instance().isAWS()) {
            connect();
            String CommitHash = getGitHash();
            if (state.equals("failed")) {
                tr.getThrowable().printStackTrace(new PrintWriter(errors));
                description = errors.toString();
            } else {
                description = null;
            }
            client.event().
                    service(machineName + " " + tr.getInstanceName() + "-" + tr.getName()).
                    state(state).
                    tags("javatests").
                    description(description).
                    ttl(eventTTL).
                    attribute("commitHash", CommitHash).
                    send();
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
                System.out.println("Cannot disconnect from riemann " + e.getMessage());
            }
            client = null;
        }
    }
}
