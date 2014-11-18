package com.forter.monitoring.utils;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static com.google.common.base.Optional.of;
import static java.util.Arrays.asList;

/*
This class represents the discovery of the riemann machine.
It is possible to use it to get the IP of a machine, based on its name / id.
*/
public class Discovery {
    private final AmazonEC2 ec2Client;

    private static class SingletonHolder {
        private static Discovery instance = new Discovery();
    }

    public static Discovery instance() {
        return SingletonHolder.instance;
    }

    private Discovery() {
        ec2Client = new AmazonEC2Client(new AWSCredentialsProviderChain(new InstanceProfileCredentialsProvider(), new EnvironmentVariableCredentialsProvider()));
    }

    public static String retrieveInstanceId() throws IOException {
        return retrieveMetadata("instance-id");
    }

    public static String retrieveMetadata(String metadata) throws IOException {
        String result = "";
        String inputLine;
        URL url = new URL("http://instance-data/latest/meta-data/" + metadata);
        URLConnection connection = url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                connection.getInputStream()));
        try {
            while ((inputLine = in.readLine()) != null) {
                result += inputLine;
            }
        }
        finally {
            in.close();
        }
        return result;
    }

    public boolean isAWS() {
        String path = System.getProperty("AWS_CLI_CONFIG_FILE", System.getProperty("user.home") + "/.aws/config");
        File f = new File(path);
        return !f.exists();
    }

    public Optional<String> retrieveName() throws IOException {
        if (!isAWS()) {
            return Optional.absent();
        }
        final String instanceId = retrieveInstanceId();
        final Instance instance = describeInstanceById(instanceId);
        return of(getInstanceName(instance));
    }

    private String getInstanceName(Instance instance) {
        return Iterables.getOnlyElement(Iterables.filter(instance.getTags(),
                new Predicate<Tag>() {
                    public boolean apply(Tag tag) {
                        return tag.getKey().equalsIgnoreCase("name");
                    }
                }
        )).getValue();
    }

    public Instance describeInstanceById(String instanceId) {
        final DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(asList(instanceId));
        return Iterables.getOnlyElement(describeInstances(request));
    }

    public Iterable<Instance> describeInstancesByName(final String name) {
        final Filter runningFilter = new Filter().withName("instance-state-name").withValues("running");
        Filter nameFilter = new Filter().withName("tag:Name").withValues(name);
        return describeInstances(
                new DescribeInstancesRequest().withFilters(nameFilter,runningFilter));
    }

    public Optional<String> getMachineName() {
        try {
            return Discovery.instance().retrieveName();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public String getRiemannIP(Optional<String> machineName) throws IOException {
        if (machineName.isPresent()) {
            String machinePrefix = (machineName.toString().startsWith("prod") ? "prod" : "develop");
            return (Iterables.get(Discovery.instance().describeInstancesByName(machinePrefix + "-riemann-instance"), 0)).getPrivateIpAddress();
        }
        else {
            throw new RuntimeException("Cannot ger riemann IP");
        }
    }


    private Iterable<Instance> describeInstances(DescribeInstancesRequest request) {
        final DescribeInstancesResult result = ec2Client.describeInstances(request);
        return Iterables.concat(
                Iterables.transform(result.getReservations(),
                        new Function<Reservation, List<Instance>>() {
                            @Override
                            public List<Instance> apply(Reservation reservation) {
                                return reservation.getInstances();
                            }
                        }));
    }
}
