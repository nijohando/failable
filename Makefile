export WORK_DIR=target

# The following environment variables are required for some tasks.
# Please export them from a secure place other than Makefile
#export GPG_SIGN_CMD="gpg --batch --yes -b -a -u <user_name>"
#export DEPLOY_REPO_USER=<user_name>
#export DEPLOY_REPO_PASS=<password>

ifndef CLJCMD
	CLJCMD=clj
endif

TASK_GROUP_CLJS=-C:dev -R:dev:cljs:test -m prj.task.cljs
TASK_GROUP_TEST=-C:dev:test -R:dev:test -m prj.task.test
TASK_GROUP_PACKAGE=-C:dev -R:dev:package -m prj.task.package
TASK_GROUP_REPL=-C:dev:test -R:dev:repl:test:package:cljs -m prj.task.repl

GROUP_ID=jp.nijohando
ARTIFACT_ID=failable
VERSION=0.4.1
JAR_FILE=$(WORK_DIR)/$(ARTIFACT_ID)-$(VERSION).jar
DEPLOY_REPO_URL=https://clojars.org/repo
LOCAL_REPO_PATH=~/.m2/repository

ifeq ($(findstring SNAPSHOT, $(VERSION)),)
	IS_RELEASE_VERSION=yes
endif

.PHONY: repl-clj repl-cljs test-clj test-cljs package deploy install clean
.DEFAULT_GOAL := repl-clj

repl-clj:
	$(CLJCMD) $(TASK_GROUP_REPL) :repl-clj

repl-cljs:
	$(CLJCMD) -C:dev:cljs -R:dev:cljs:test -m figwheel.main -co dev/dev.cljs.edn -r

test-clj:
	$(CLJCMD) $(TASK_GROUP_TEST) :test-clj

test-cljs:
	$(CLJCMD) $(TASK_GROUP_CLJS) :test-cljs

pom.xml:
	$(CLJCMD) -Spom
	$(CLJCMD) $(TASK_GROUP_PACKAGE) :update-pom pom.xml $(GROUP_ID) $(ARTIFACT_ID) $(VERSION)
ifdef IS_RELEASE_VERSION
	$(GPG_SIGN_CMD) pom.xml
endif

$(JAR_FILE):
	mkdir -p $(WORK_DIR)
	jar cf $(JAR_FILE) -C src jp
ifdef IS_RELEASE_VERSION
	$(GPG_SIGN_CMD) $(JAR_FILE)
endif

package: pom.xml $(JAR_FILE)

deploy: package
	$(CLJCMD) $(TASK_GROUP_PACKAGE) :deploy pom.xml $(JAR_FILE) $(DEPLOY_REPO_URL)

install: package
	$(CLJCMD) $(TASK_GROUP_PACKAGE) :install pom.xml $(JAR_FILE) $(LOCAL_REPO_PATH)

clean:
	rm -rf ${WORK_DIR}
	rm -f pom.xml pom.xml.asc
