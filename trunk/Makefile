SOURCE_FILES=$(wildcard *.java)
TAR_FILE_OF_THE_DAY=`date +revtool-%Y-%m-%d.tar`

BOOT_CLASS_PATH=/System/Library/Frameworks/JavaVM.framework/Classes/classes.jar
BOOT_CLASS_PATH=/home/elliotth/download/j2sdk1.4.2/jre/lib/rt.jar

revtool: $(SOURCE_FILES)
	@echo Recompiling the world...
	@make clean; \
	 mkdir classes; \
	 jikes -bootclasspath $(BOOT_CLASS_PATH) -d classes +P $(SOURCE_FILES)

clean:
	@$(RM) -rf classes

dist: revtool
	cd ..; \
	tar cvf $(TAR_FILE_OF_THE_DAY) revtool/; \
	gzip $(TAR_FILE_OF_THE_DAY); \
	scp $(TAR_FILE_OF_THE_DAY).gz kenny:~/
