package org.apache.ranger.security.oidc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.authentication.util.SsoConfigurationUtil;

public class OidcUtil {
  public static final boolean IS_OIDC_ENABLED;

  static {
    IS_OIDC_ENABLED = new Configuration().getBoolean(SsoConfigurationUtil.HADOOP_JWT_ENABLED, false);
  }
}
