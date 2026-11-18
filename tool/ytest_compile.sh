clear

echo "rm -rf obj/net"
rm -rf obj/net

echo "rm -fv bin/*.jar"
rm -fv bin/*.jar
 
echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -d obj src/net/murat/math/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/math/*.java

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -d obj src/net/murat/util/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/util/*.java

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -d obj src/net/murat/light/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/light/*.java

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -d obj src/net/murat/material/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/material/*.java

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/shape/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/shape/*.java

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/lovert/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/lovert/*.java

#echo "Running..."
#echo "java -cp obj net.murat.lovert.MuratRayTracer"
#java -cp obj net.murat.lovert.MuratRayTracer

echo "javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/gui/*.java"
javac -source 1.8 -target 1.8 -parameters -encoding UTF-8 -sourcepath src -cp obj -g -proc:none -nowarn -O -Xlint:deprecation -d obj src/net/murat/gui/*.java

#java -cp obj net.murat.gui.MuratRayTracerGUI &

echo "jar cmf MANIFEST.MF bin/guijtracer.jar -C obj . -C . src *.java *.sh"
jar cmf MANIFEST.MF bin/guijtracer.jar -C obj . -C . src *.java *.sh

echo "rm -rf obj/net"
rm -rf obj/net

echo "java -jar bin/guijtracer.jar &"
java -jar bin/guijtracer.jar &
