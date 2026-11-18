echo "Compiling uikit package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/uikit/*.java

echo "Compiling math package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/math/*.java

echo "Compiling util package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/util/*.java

echo "Compiling light package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/light/*.java

echo "Compiling shape package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/shape/*.java

echo "Compiling material package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/material/*.java

echo "Compiling lovert package..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/lovert/*.java

echo "Compiling gui/SceneParser clase..."
javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-36/android.jar" -sourcepath "src" -cp "/usr/java/android/platforms/android-36/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" src/net/murat/gui/SceneParser.java
