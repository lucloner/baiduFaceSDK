#!/bin/sh
##############################################################################################
# 导出sdk功能
# 作者：yangrui09
#
##############################################################################################
echo   ------- Begin -------

# config agile compile 
ANDROID_SDK_HOME=/home/scmtools/buildkit/android-sdk
if [ -d "$ANDROID_SDK_HOME" ]; then  
    echo "ANDROID_SDK_HOME exist，so current is aigile"
	JAVA_HOME=/home/scmtools/buildkit/jdk-1.8u92
    ANDROID_NDK_HOME=/home/scmtools/buildkit/android-ndk-r12b
	JAVA_GRADLE=/home/scmtools/buildkit/gradle/gradle-2.4/bin
	PATH=$JAVA_GRADLE:$ANDROID_SDK_HOME:$JAVA_HOME:$ANDROID_NDK_HOME:$PATH 
fi  

clear
date

#定义当前工作空间

workpath=$(pwd)
outputpath=$workpath"/output"
outputsdkdemopath=$outputpath"/demo"

echo "${workpath}"
echo "${outputpath}"
echo "${outputsdkdemopath}"


echo "java evn：" `java -version`
echo 1------- create dir -------

rm -r $outputpath
mkdir $outputpath
mkdir $outputsdkdemopath



echo   ---------------------------------------------------------------
echo 2 ------- begin build -------
#./gradlew assembleRelease
./gradlew clean
./gradlew assembleDebug


echo   ---------------------------------------------------------------
echo 3 ------- copy app-debug.apk -------
cp $workpath"/app/build/outputs/apk/app-debug.apk" $outputpath


echo   ---------------------------------------------------------------
echo 4 ------- copy demo  -------
ls ${workpath}
rm -r $workpath"/build"
rm -r $workpath"/app/build"
rm -r $workpath"/library/build"

echo $workpath"/app"
cp -r -f $workpath"/library/" $outputsdkdemopath
cp -r -f $workpath"/app/" $outputsdkdemopath
cp $workpath"/build.gradle" $outputsdkdemopath
cp -r $workpath"/gradle" $outputsdkdemopath
cp $workpath"/gradle.properties" $outputsdkdemopath
cp $workpath"/gradlew" $outputsdkdemopath
#cp $localpropertiespath $outputsdkdemopath
cp $workpath"/settings.gradle" $outputsdkdemopath

ls $outputsdkdemopath


echo   ---------------------------------------------------------------
echo 5.0 ------- to zip -------
cd $outputpath
zip -r FaceRecognizeOffline_V3.2.1_R.zip $outputsdkdemopath

echo $outputpath
ls $outputpath


echo   ------- End -------

