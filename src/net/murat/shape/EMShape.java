package net.murat.shape;

import java.util.List;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Interface representing all renderable geometric shapes in the ray tracer. Streamlined and fully
 * optimized to run 100% garbage-free in high-performance rendering loops.
 */
public interface EMShape {

  // =========================================================================
  // MEVCUT METODLAR (Aynen korunacak)
  // =========================================================================

  boolean intersect(Ray ray, Intersection target);

  List<IntersectionInterval> intersectAll(Ray ray);

  void setMaterial(Material material);

  void setTransform(Matrix4 transform);

  void setAnimationTransforms(Matrix4[] transform);

  Vector3 getNormalAt(Point3 point);

  Material getMaterial();

  Matrix4 getTransform();

  Matrix4 getInverseTransform();

  Matrix4[] getAnimationTransforms();

  int getShadowColor();

  void setShadowColor(int packedArgbColor);

  double getShadowBias();

  void setShadowBias(double bias);

  String getNameInfo();

  String getName();

  void setName(String name);

  String getOtherAnimationInfo();

  void setOtherAnimationInfo(String str);

  boolean isVisibleSpecial();

  boolean isVisible();

  boolean isShadowEnable();

  boolean isShadowOnly();

  boolean isReflective();

  boolean isRefractive();

  void setVisibleSpecial(boolean visible);

  void setVisible(boolean visible);

  void setShadowEnable(boolean enable);

  void setShadowOnly(boolean only);

  void setReflective(boolean rfl);

  void setRefractive(boolean rfr);

  // =========================================================================
  // MESH METHODS
  // =========================================================================
  /**
   * Generates triangle mesh representation of this shape. Each shape implements its own mesh
   * generation logic.
   *
   * @return List of triangles forming the mesh
   */
  List<Triangle> generateMesh();

  /**
   * Returns the triangles if already generated and cached, or null if not. For performance, shapes
   * can cache the mesh after first generation. Default implementation returns null.
   */
  List<Triangle> getTriangles();

  /**
   * Exports the shape as a complete .obj file content. Includes vertices, triangles, material, and
   * transform info. Each shape implements its own export logic.
   *
   * @return String containing the full .obj file content
   */
  String toObjString();
}
