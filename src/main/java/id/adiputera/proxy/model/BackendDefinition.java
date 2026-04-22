package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BackendDefinition {

    private String baseUrl;

    private int connectTimeout;

    private int readTimeout;
}
