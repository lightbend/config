package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

import java.util.Map;

public class ConfigConditional {

    private SubstitutionExpression left;
    private AbstractConfigValue right;
    private SimpleConfigObject body;

    ConfigConditional(SubstitutionExpression left, AbstractConfigValue right, SimpleConfigObject body) {
        this.left = left;
        this.right = right;
        this.body = body;

        if (this.left.optional()) {
            throw new ConfigException.BugOrBroken("Substitutions in conditional expressions cannot be optional");
        }
    }

    public SimpleConfigObject resolve(ResolveContext context, ResolveSource source) {
        try {
            AbstractConfigValue val = source.lookupSubst(context, this.left, 0).result.value;

            if (val.equals(this.right)) {
                return this.body;
            } else {
                return SimpleConfigObject.empty();
            }
        } catch (AbstractConfigValue.NotPossibleToResolve e){
            throw new ConfigException.BugOrBroken("Could not resolve left side of conditional expression: " + this.left.toString());
        }
    }
}
