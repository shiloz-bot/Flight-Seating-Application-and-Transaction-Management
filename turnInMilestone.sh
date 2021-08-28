#!/bin/bash
TAG=milestone1

#check no uncommitted changes.
(git status | grep -q modified:) &&  echo  'Error. There are uncommitted changes in your working directory. You can do "git status" to see them.
Please commit or stash uncommitted changes before submitting' && exit 1
COMMIT=$(git log | head -n 1 |  cut -b 1-14)
if (git tag $TAG 2>/dev/null)
then
    echo "Created tag '$TAG' pointing to $COMMIT"
else
    git tag -d $TAG && git tag $TAG
    git push --delete origin $TAG
    echo "Re-creating tag '$TAG'... (now $COMMIT)"
fi
echo "Now syncing with origin..."
git push origin $TAG
echo "Please verify in gitlab that your tag '$TAG' matches what you expect. "
