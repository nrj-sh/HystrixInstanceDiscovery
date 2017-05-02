package com.proptiger.hystrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceDiscovery;

public class NameBasedAwsInstanceDiscovery implements InstanceDiscovery {

    private DynamicStringProperty INSTANCE_NAME_REGEX =
            DynamicPropertyFactory.getInstance().getStringProperty("instance.name.regex", null);

    private AmazonEC2             ec2                 =
            AmazonEC2ClientBuilder.standard().withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                    .withRegion(Regions.AP_SOUTHEAST_1).build();

    @Override
    public Collection<Instance> getInstanceList() throws Exception {

        if (INSTANCE_NAME_REGEX.get() == null) {
            return null;
        }

        DescribeInstancesResult result = ec2.describeInstances();
        List<Reservation> reservations = result.getReservations();

        List<Instance> hytrixInstances = new ArrayList<>();

        for (Reservation reservation : reservations) {
            List<com.amazonaws.services.ec2.model.Instance> instances = reservation.getInstances();
            for (com.amazonaws.services.ec2.model.Instance instance : instances) {
                String clusterName = getName(INSTANCE_NAME_REGEX.get(), instance);

                if (clusterName != null) {
                    hytrixInstances.add(new Instance(instance.getPrivateDnsName(), clusterName, true));
                }
            }
        }

        return hytrixInstances;
    }

    private static String getName(String regex, com.amazonaws.services.ec2.model.Instance instance) {
        return instance.getTags().stream().filter(tag -> tag.getKey().equals("Name") && tag.getValue().matches(regex))
                .map(tag -> tag.getValue()).findFirst().orElse(null);
    }
}
