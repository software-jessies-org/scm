SOURCE_FILES=$(wildcard *.java)

revtool: $(SOURCE_FILES)
	mkdir classes; jikes -d classes +P $(SOURCE_FILES)

clean:
	$(RM) -rf classes
