package tw.com.providers;

import java.time.ZonedDateTime;

public interface ProvidesNow {
    ZonedDateTime getUTCNow();
}
