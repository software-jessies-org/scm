SOURCE_FILES=$(wildcard *.java)
TAR_FILE_OF_THE_DAY=`date +revtool-%Y-%m-%d.tar`

revtool: $(SOURCE_FILES)
	@echo Recompiling the world...
	@make clean; mkdir classes; jikes -d classes +P $(SOURCE_FILES)

clean:
	@$(RM) -rf classes

dist: revtool
	cd ..; tar cvf $(TAR_FILE_OF_THE_DAY) revtool/; gzip $(TAR_FILE_OF_THE_DAY)
