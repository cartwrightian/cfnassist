package tw.com.parameters;

import software.amazon.awssdk.services.ec2.model.AvailabilityZone;

import java.util.Map;

public interface ProvidesZones {
    Map<String,AvailabilityZone> getZones();
}
