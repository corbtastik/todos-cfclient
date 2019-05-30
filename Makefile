DEV_ROOT=/Users/corbett/Desktop/shop

clean:
	rm ${DEV_ROOT}/jars/*.jar

jarfolder:
	mkdir -p ${DEV_ROOT}/jars

listjars:
	ls -lrt ${DEV_ROOT}/jars

buildjars:
	pushd ${DEV_ROOT}/todos-webui && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-api   && ./mvnw clean package -DskipTests && popd
	pushd ${DEV_ROOT}/todos-edge  && ./mvnw clean package -DskipTests && popd

copyjars: jarfolder
	cp ${DEV_ROOT}/todos-webui/target/todos-webui-1.0.0.SNAP.jar ${DEV_ROOT}/jars
	cp ${DEV_ROOT}/todos-api/target/todos-api-1.0.0.SNAP.jar     ${DEV_ROOT}/jars
	cp ${DEV_ROOT}/todos-edge/target/todos-edge-1.0.0.SNAP.jar   ${DEV_ROOT}/jars

build:
	./mvnw clean package -DskipTests
	
run:
	java -jar ./target/todos-cfclient-1.0.0.SNAP.jar --spring.profiles.active=pws
