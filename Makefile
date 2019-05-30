clean:
	rm ./src/main/resources/*.jar

build:
	./mvnw clean package -DskipTests

buildjars:
	pushd ~/dev/show/todos-webui && ./mvnw clean package -DskipTests && popd
	pushd ~/dev/github/todos-apps/todos-api && ./mvnw clean package -DskipTests && popd
	pushd ~/dev/github/todos-apps/todos-edge && ./mvnw clean package -DskipTests && popd

copyjars:
	cp ~/dev/show/todos-webui/target/todos-webui-1.0.0.SNAP.jar ./src/main/resources/
	cp ~/dev/github/todos-apps/todos-api/target/todos-api-1.0.0.SNAP.jar ./src/main/resources/
	cp ~/dev/github/todos-apps/todos-edge/target/todos-edge-1.0.0.SNAP.jar ./src/main/resources/
	
run:
	java -jar ./target/cf-client-0.0.1-SNAPSHOT.jar
