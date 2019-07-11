BRANCH=cloud

mkdir-app:
	mkdir .apps

clone-all:
	pushd ./.apps && git clone https://github.com/corbtastik/todos-redis && git clone https://github.com/corbtastik/todos-mysql && git clone https://github.com/corbtastik/todos-edge && git clone https://github.com/corbtastik/todos-api && git clone https://github.com/corbtastik/todos-webui && git clone https://github.com/corbtastik/todos-app && popd

build-all:
	pushd ./.apps/todos-app   && git checkout master && ./mvnw clean package -DskipTests && popd
	pushd ./.apps/todos-redis && git checkout ${BRANCH} && ./mvnw clean package -DskipTests && popd
	pushd ./.apps/todos-mysql && git checkout ${BRANCH} && ./mvnw clean package -DskipTests && popd
	pushd ./.apps/todos-webui && git checkout ${BRANCH} && ./mvnw clean package -DskipTests && popd
	pushd ./.apps/todos-api   && git checkout ${BRANCH} && ./mvnw clean package -DskipTests && popd
	pushd ./.apps/todos-edge  && git checkout ${BRANCH} && ./mvnw clean package -DskipTests && popd

mkdir-jars:
	mkdir -p ./.apps/jars

copy-jars: mkdir-jars
	cp ./.apps/todos-app/target/todos-app-1.0.0.SNAP.jar ./.apps/jars
	cp ./.apps/todos-redis/target/todos-redis-1.0.0.SNAP.jar ./.apps/jars
	cp ./.apps/todos-mysql/target/todos-mysql-1.0.0.SNAP.jar ./.apps/jars
	cp ./.apps/todos-webui/target/todos-webui-1.0.0.SNAP.jar ./.apps/jars
	cp ./.apps/todos-api/target/todos-api-1.0.0.SNAP.jar     ./.apps/jars
	cp ./.apps/todos-edge/target/todos-edge-1.0.0.SNAP.jar   ./.apps/jars

clean:
	rm -rf ./.apps

setup: mkdir-app clone-all build-all copy-jars


