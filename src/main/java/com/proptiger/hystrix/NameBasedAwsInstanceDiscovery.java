package com.proptiger.hystrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceDiscovery;

public class NameBasedAwsInstanceDiscovery implements InstanceDiscovery {
    private static Logger       logger              = LoggerFactory.getLogger(NameBasedAwsInstanceDiscovery.class);

    final DynamicStringProperty INSTANCE_NAME_REGEX =
            DynamicPropertyFactory.getInstance().getStringProperty("instance.name.regex", null);

    final Pattern               pattern;

    final AmazonEC2             ec2;

    public NameBasedAwsInstanceDiscovery() {
        ec2 = new AmazonEC2Client();
        ec2.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1));

        pattern = Pattern.compile(INSTANCE_NAME_REGEX.get());

    }

    @Override
    public Collection<Instance> getInstanceList() throws Exception {

        logger.info("Started instance discovery");

        if (INSTANCE_NAME_REGEX.get() == null) {
            return null;
        }

        DescribeInstancesResult result = ec2.describeInstances();
        List<Reservation> reservations = result.getReservations();

        List<Instance> hytrixInstances = new ArrayList<>();

        for (Reservation reservation : reservations) {
            List<com.amazonaws.services.ec2.model.Instance> instances = reservation.getInstances();
            for (com.amazonaws.services.ec2.model.Instance instance : instances) {
                String clusterName = getName(pattern, instance);

                if (clusterName != null) {
                    hytrixInstances.add(new Instance(instance.getPrivateDnsName(), clusterName, true));
                }
            }
        }

        logger.info("Instance list {}", hytrixInstances);

        logger.info("End instance discovery");

        return hytrixInstances;
    }

    private static String getName(Pattern regex, com.amazonaws.services.ec2.model.Instance instance) {

        String name = instance.getTags().stream()
                .filter(tag -> tag.getKey().equals("Name") && regex.matcher(tag.getValue()).matches())
                .map(tag -> tag.getValue().toLowerCase()).findFirst().orElse(null);

        if (name != null) {
            Matcher matcher = regex.matcher(name);
            while (matcher.find()) {
                name = matcher.group(1);
            }
        }

        return name;
    }

}
