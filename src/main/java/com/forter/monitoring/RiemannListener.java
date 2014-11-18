package com.forter.monitoring;

import java.io.IOException;
import com.aphyr.riemann.client.RiemannClient;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.forter.monitoring.utils.RiemannDiscovery;

import com.google.common.collect.Iterables;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

public class RiemannListener extends TestListenerAdapter{
    private String riemannIP;
    private  String machineName;
    private final int riemannPort = 5555;
    private RiemannClient client;
    private String description;
    private RiemannDiscovery DiscoveryInstance;

    public void connect() {
        if (client == null) {
            try {
                machineName = getMachineName().get();;
                riemannIP = getRiemannIP(DiscoveryInstance);
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

    private String getRiemannIP(RiemannDiscovery discover) throws IOException {
        String machinePrefix = (machineName.startsWith("prod") ? "prod" : "develop");
        return (Iterables.get(discover.describeInstancesByName(machinePrefix + "-riemann-instance"), 0)).getPrivateIpAddress();
    }

    private Optional<String> getMachineName() {
        try {
            return new RiemannDiscovery().retrieveName();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void sendEvent(ITestResult tr, String state) {
        DiscoveryInstance = new RiemannDiscovery();
        if (DiscoveryInstance.isAWS()) {
            connect();
            if (state == "failure") {
                description = tr.getThrowable().toString();
            } else {
                description = null;
            }
            client.event().
                    service(machineName + " " + tr.getInstanceName() + "-" + tr.getName()).
                    state(state).
                    tags("storm").
                    description(description).
                    ttl(20).
                    send();
        }
    }

    @Override
    public void onTestFailure(ITestResult tr) {
        sendEvent(tr, "failure");
    }

    public void onConfigurationFailure(ITestResult tr) {
        sendEvent(tr, "failure");
    }
    @Override
    public void onTestSkipped(ITestResult tr) {
        sendEvent(tr, "skipped");
    }

    @Override
    public void onTestSuccess(ITestResult tr) {
        sendEvent(tr, "pass");
    }
}