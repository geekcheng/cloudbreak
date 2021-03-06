package com.sequenceiq.cloudbreak.service.stack.connector.azure;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.domain.AzureCredential;

@Component
public class KeyGeneratorService {

    public void generateKey(String user, AzureCredential azureCredential, String alias, String path) throws Exception {
        String command = StringUtils.join(new String[]{
                "keytool",
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keystore", path,
                "-keysize", "2048",
                "-keypass", AzureStackUtil.DEFAULT_JKS_PASS,
                "-storepass", AzureStackUtil.DEFAULT_JKS_PASS,
                "-dname", "cn=" + user + azureCredential.getPostFix() + ",ou=engineering,o=company,c=US"
        }, " ");
        Process p = Runtime.getRuntime().exec(command);
        p.waitFor();
    }

    public void generateSshKey(String path) throws IOException, InterruptedException {
        String[] commands = new String[]{
                //String.format("openssl req -x509 -nodes -days 365 -newkey rsa:1024 -keyout %s.pem -out %s.pem -batch", path, path),
                String.format("openssl x509 -inform pem -in %s.pem -outform der -out %s.cer", path, path),
                String.format("openssl pkcs12 -export -out %s.p12 -inkey %s.pem -in %s.pem -password pass:password", path, path, path),
                String.format("keytool -importkeystore -destkeystore %s.jks -srcstoretype PKCS12 -srckeystore %s.p12 "
                        + "-storepass password -srcstorepass password -noprompt", path, path)
        };

        for (String command : commands) {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
        }
    }

}
