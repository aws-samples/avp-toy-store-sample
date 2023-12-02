package org.example.authorizerpolicies;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Statement.Builder.class)
public class Statement {

    public final String Action = "execute-api:Invoke";

    public String Effect;
    public String Resource;

    private Statement(Builder builder) {
        this.Effect = builder.effect;
        this.Resource = builder.resource;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String effect;
        private String resource;

        private Builder() { }

        public Builder effect(String effect) {
            if(StringUtils.lowerCase(effect).equals("ALLOW"))
              this.effect = "Allow";
            else
                this.effect = "Deny";
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Statement build() {
            return new Statement(this);
        }
    }
}