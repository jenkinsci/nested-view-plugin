# Nested View

A view type to allow grouping job views into multiple levels instead of one big list of tabs.
This is an awesome legacy extension for Jenkins.
No pipelines, no folders, thousands of jobs with ten thousands of views?
This can help you to make an order!

## Introduction

This plugin adds a new view type that can be selected when adding job views.
This view does not show any jobs, but rather contains another set of views.
By default, clicking on the view tab for a nested view shows a list of the subviews it contains (with folder icons).
You can also configure a default subview to bypass this intermediate page and jump directly to that default view.
Now the view tabs across the top show the views in this nested view, and the job list is for this default subview.
This streamlines the navigation between views, but makes it harder to find the *Edit View* link for the nested view itself.
Once a default subview has been assigned, navigate to the edit page by first clicking the plus ("+") icon in the view tabs (for adding a new subview) and then find the *Edit View* link in the sidepanel.

## Search extension

A long time ago, Nested View had a bug where search was unable to crawl inside nested views.
Fixing that led to overriding the search engine.
Details about the search implementation can be found here: https://jvanek.fedorapeople.org/JenkinsSearch/

The search has its pros and cons.
It can be enabled/disabled in global/system settings <br>
![screenshot from system settings](https://issues.jenkins.io/secure/attachment/63238/image-2024-09-06-00-17-43-606.png)<br>
or temporarily by appending `-X:` to the query

The search knows logical operators and can search also in builds via display name/comment and showing build details and statistics.
This plugin supports customizable saving of search history.
Unfortunately, you cannot search in settings or have undocumented issues in your Pipeline job's environment.

## Testing build

    mvn hpi:run

## Performing release

Always test connection before release.

Testing connection: `ssh -T git@github.com`

Release: `mvn release:prepare release:perform`
