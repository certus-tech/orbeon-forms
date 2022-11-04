# CTA Orbeon WEB-INF Resources

Any Orbeon files that can be overridden within the WEB-INF/resources directory should go here. Note that for local
development, we are symlinking parts of this directory to the respective extension so that it still works locally.
However, during actual Orbeon deployments, [orbeon-form-builder:install.sh](https://git.int.certus-tech.com/component/orbeon-form-builder/-/blob/master/install/orbeon-forms/install.sh)
copies these resources into the orbeon-war for us (for some unknown reason, the changes being in our extension jars works
locally but does not work when deployed in CI systems).

Ideally we wouldn't need to symlink at all but could update the build.sbt but our knowledge of Scala is limited.
Alternatively, we could also just override the Orbeon source files directly (as before) but we are trying to avoid
doing this if possible to make upgrading easier.