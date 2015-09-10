package tw.com.parameters;

import com.amazonaws.services.ec2.model.AvailabilityZone;

import java.util.Map;

public interface ProvidesZones {
    Map<String,AvailabilityZone> getZones();
}
