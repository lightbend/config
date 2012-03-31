/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl;

import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.impl.AbstractConfigValue.NotPossibleToResolve;

/**
 * TODO there is no reason for this class to exist, will remove in upcoming
 * commit
 */
final class SubstitutionResolver {
    SubstitutionResolver() {

    }

    static AbstractConfigValue resolve(AbstractConfigValue value, AbstractConfigObject root,
            ConfigResolveOptions options, Path restrictToChildOrNull) throws NotPossibleToResolve {
        SubstitutionResolver resolver = new SubstitutionResolver();
        ResolveContext context = new ResolveContext(root, options, restrictToChildOrNull);

        return context.resolve(resolver, value);
    }

    static AbstractConfigValue resolveWithExternalExceptions(AbstractConfigValue value,
            AbstractConfigObject root, ConfigResolveOptions options) {
        SubstitutionResolver resolver = new SubstitutionResolver();
        ResolveContext context = new ResolveContext(root, options, null /* restrictToChild */);

        try {
            return context.resolve(resolver, value);
        } catch (NotPossibleToResolve e) {
            throw e.exportException(value.origin(), null);
        }
    }
}
