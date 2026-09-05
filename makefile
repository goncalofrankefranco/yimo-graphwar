all:

	./compile.sh

docker-graphwar:

	## build graphwar inside of a container
	docker run --rm \
		-v ${PWD}:/compile \
		graphwar/build

docker-image:

	## build docker image
	docker build -t graphwar/build .

run-client:

	## run graphwar on a container
	docker run --rm \
		-v ${PWD}:/compile \
		-v /tmp/.X11-unix:/tmp/.X11-unix \
		-e DISPLAY=${DISPLAY} \
		--network host \
		graphwar/build \
		java -jar build/local/graphwar.jar

clean:

	rm -rf build/local
