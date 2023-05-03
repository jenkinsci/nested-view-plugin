# jenkins nested-view-plugin

An awesome legacy extension for jenkins - no pipelines, no folders, thousands of jobs with ten thousands of views? This can help you to make an order!

## Search extension
NestedViews had long ago bug, that search was unable to crawl inside nested views. To fix that, lead to the overriding of serch engine.
Deatails about search impl can be found: https://jvanek.fedorapeople.org/jenkinsSearch/

The search have its pros and cons. It can be enabled/disbaled in global settings, or temorarily by `-X:` in query.

The search knows logical operators and can search also in builds via displayName and comment and hsowinf build details and statistics, and have customizable saving of search history. Unluckily, can not search in settings or have undocumented issues in pipelines environemnt.

## Testing build

    mvn hpi:run

## Performing release

Always test connection before release.

Testing connection: `ssh -T git@github.com`

Release: `mvn release:prepare release:perform`
