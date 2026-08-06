# Submitting Thwart to F-Droid

Everything the inclusion policy asks for is already in this repository. What is
left needs a GitLab account and Docker, so it has to be run by hand.

The merge request should come from the author's own account: the policy asks for
confirmation that the author does not oppose inclusion, and an MR from anyone
else is a stranger submitting somebody else's app.

## 1. Fork and branch

Fork <https://gitlab.com/fdroid/fdroiddata>, then:

```
git clone git@gitlab.com:<you>/fdroiddata.git ~/fdroiddata
cd ~/fdroiddata
git checkout -b com.hasyame.marvelchampions
cp /path/to/Thwart/fdroid/com.hasyame.marvelchampions.yml metadata/
```

## 2. Check it in the container

```
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
docker run --rm -itu vagrant --entrypoint /bin/bash \
  -v ~/fdroiddata:/build:z \
  -v ~/fdroidserver:/home/vagrant/fdroidserver:Z \
  registry.gitlab.com/fdroid/fdroidserver:buildserver
```

Inside it:

```
. /etc/profile
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver"
export JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 > /dev/null \
  | grep 'java.home' | awk -F'=' '{print $2}' | tr -d ' ')
cd /build
fdroid readmeta
fdroid rewritemeta com.hasyame.marvelchampions
fdroid checkupdates --allow-dirty com.hasyame.marvelchampions
fdroid lint com.hasyame.marvelchampions
fdroid build com.hasyame.marvelchampions
```

`rewritemeta` reformats the file — commit whatever it produces rather than
arguing with it. Expect `lint` to want small changes on a first submission; that
is normal and not a sign anything is wrong.

If Docker is not to hand, pushing the branch to your fork also runs the same
checks in GitLab CI, which is slower to iterate on but needs nothing installed.

## 3. Merge request

```
git add metadata/com.hasyame.marvelchampions.yml
git commit -m "New App: com.hasyame.marvelchampions"
git push origin com.hasyame.marvelchampions
```

Open the MR against `fdroiddata` with the **New App** label, and watch it for
questions — the reviewers will ask, and a fast answer is what keeps it moving.

## What a reviewer will find

- MIT, with a LICENSE file
- No Firebase, no GMS, no play-services: checked in the declared dependencies,
  the resolved `releaseRuntimeClasspath`, and the built APK
- `google()` and `mavenCentral()` only; no JitPack, no flatDir, no binary blobs
- `NonFreeNet` declared up front — card data comes from MarvelCDB
- Release signing falls back to the debug key when `keystore.properties` is
  absent, so their buildserver can build it without your keystore
- Every release tagged, `versionCode` and `versionName` in the standard place,
  so `UpdateCheckMode: Tags` needs no `UpdateCheckData`
