DEV_ROOT=/Users/corbs/dev/github/todos-apps

delete:
	cf delete hi-todos-api -f && cf delete hi-todos-webui -f && cf delete hi-todos-edge -f

jarfolder:
	mkdir -p ${DEV_ROOT}/jars/master
	mkdir -p ${DEV_ROOT}/jars/cloud

cleanjars:
	rm -rf ${DEV_ROOT}/jars/

listjars:
	ls -lrt ${DEV_ROOT}/jars/master && ls -lrt ${DEV_ROOT}/jars/cloud

buildjars:
	pushd ${DEV_ROOT}/todos-webui && git checkout master && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-api   && git checkout master && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-edge  && git checkout master && ./mvnw clean package -DskipTests && popd

buildscsjars:
	pushd ${DEV_ROOT}/todos-webui && git checkout cloud && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-api   && git checkout cloud && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-edge  && git checkout cloud && ./mvnw clean package -DskipTests && popd

copyjars: jarfolder buildjars
	cp ${DEV_ROOT}/todos-webui/target/todos-webui-1.0.0.SNAP.jar ${DEV_ROOT}/jars/master
	cp ${DEV_ROOT}/todos-api/target/todos-api-1.0.0.SNAP.jar     ${DEV_ROOT}/jars/master
	cp ${DEV_ROOT}/todos-edge/target/todos-edge-1.0.0.SNAP.jar   ${DEV_ROOT}/jars/master

copyscsjars: jarfolder buildscsjars
	cp ${DEV_ROOT}/todos-webui/target/todos-webui-1.0.0.SNAP.jar ${DEV_ROOT}/jars/cloud
	cp ${DEV_ROOT}/todos-api/target/todos-api-1.0.0.SNAP.jar     ${DEV_ROOT}/jars/cloud
	cp ${DEV_ROOT}/todos-edge/target/todos-edge-1.0.0.SNAP.jar   ${DEV_ROOT}/jars/cloud

build:
	./mvnw clean package
	
runpws:
	java -jar ./target/todos-cfclient-1.0.0.SNAP.jar --spring.profiles.active=pws

run:
	java -jar ./target/todos-cfclient-1.0.0.SNAP.jar --jars.folder=/Users/corbs/dev/github/todos-apps/jars/master

runscs:
	java -jar ./target/todos-cfclient-1.0.0.SNAP.jar --jars.folder=/Users/corbs/dev/github/todos-apps/jars/cloud

refresh:
	echo '{}' | http scsintern-todos-edge.apps.retro.io/actuator/refresh

