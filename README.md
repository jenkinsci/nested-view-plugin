# jenkins nested-view-plugin

An awesome legacy extension for jenkins - no pipelines, no folders, thousands of jobs with ten thousands of views? This can help you to make an order!

The search is now implemented. If you have any custom search on, do not update. 
Deatails about search impl can be found: https://jvanek.fedorapeople.org/jenkinsSearch/

If the search will become obstacle, then the plugin probably can be split to two. On huge instances (10k+) do not torture the search engine with .* or ! negation. The paging and some minimal query are on todo list (see code comments)

## Testing build

    mvn hpi:run
## Performing release

Always test connection before release.

Testing connection: `ssh -T git@github.com`

Release: `mvn release:prepare release:perform`
