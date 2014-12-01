package com.forter.monitoring;

import java.io.IOException;
import com.aphyr.riemann.client.RiemannClient;
import com.forter.monitoring.utils.Discovery;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

public class RiemannListener extends TestListenerAdapter{
    private String riemannIP;
    private String machineName;
    private final int riemannPort = 5555;
    private final int eventTTL = 20;
    private RiemannClient client;
    private String description;

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

    public void sendEvent(ITestResult tr, String state) {
        if (Discovery.instance().isAWS()) {
            connect();
            if (state.equals("failed")) {
                description = tr.getThrowable().getStackTrace().toString();
            } else
                description = null;
            }
            client.event().
                    service(machineName + " " + tr.getInstanceName() + "-" + tr.getName()).
                    state(state).
                    tags("javatests").
                    description(description).
                    ttl(eventTTL).
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
}
