package net.murat.math;

/**
 * Represents a 4x4 matrix for 3D transformations.
 *
 * <p>Design goal: zero garbage in hot paths (ray tracing inner loops). Every instance method that
 * can mutate a pre-allocated target and avoid heap allocation now does so, including a few call
 * sites that previously allocated hidden temporaries on every invocation ({@link
 * #inverse(Matrix4)}, {@link #transformRay(Ray)}, {@link #multiply(Matrix4)}).
 *
 * <p>Methods that are documented as {@code ALLOCATES} are kept only because their signature has no
 * target parameter to write into (mostly the static factory methods and the no-arg legacy overloads
 * of {@link #inverse()} and {@link #inverseTransposeForNormal()}). These are intended for one-time
 * scene setup / parsing, not per-pixel or per-ray hot paths. Prefer the target-parameter overload
 * wherever one is available.
 */
public class Matrix4 {

  private final double[] m;

  private double tx = 0.0, ty = 0.0, tz = 0.0;
  private double rx = 0.0, ry = 0.0, rz = 0.0;
  private double sx = 1.0, sy = 1.0, sz = 1.0;

  // Scratch objects reused across calls on this instance to keep
  // inverse()/inverseTransposeForNormal()/transformRay() GC-free.
  // NOTE: not thread-safe, matching the rest of this GC-friendly
  // engine's convention of per-instance scratch state.
  private final Matrix3 scratchMatrix3_1 = new Matrix3();
  private final Matrix3 scratchMatrix3_2 = new Matrix3();
  private final Matrix3 scratchMatrix3_3 = new Matrix3();
  private final Vector3 scratchTranslation = new Vector3();
  private final Vector3 scratchInvTranslation = new Vector3();
  private final Point3 scratchRayOrigin = new Point3();
  private final Vector3 scratchRayDirection = new Vector3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public Matrix4() {
    m = new double[16];
    setIdentity();
  }

  public Matrix4(
      double m00,
      double m01,
      double m02,
      double m03,
      double m10,
      double m11,
      double m12,
      double m13,
      double m20,
      double m21,
      double m22,
      double m23,
      double m30,
      double m31,
      double m32,
      double m33) {
    m = new double[16];
    set(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33);
  }

  public Matrix4(Matrix4 other) {
    m = new double[16];
    set(other);
  }

  // =========================================================================
  // SETTERS
  // =========================================================================

  public Matrix4 setIdentity() {
    m[0] = 1.0;
    m[1] = 0.0;
    m[2] = 0.0;
    m[3] = 0.0;
    m[4] = 0.0;
    m[5] = 1.0;
    m[6] = 0.0;
    m[7] = 0.0;
    m[8] = 0.0;
    m[9] = 0.0;
    m[10] = 1.0;
    m[11] = 0.0;
    m[12] = 0.0;
    m[13] = 0.0;
    m[14] = 0.0;
    m[15] = 1.0;
    updateTransformValues();
    return this;
  }

  public Matrix4 set(
      double m00,
      double m01,
      double m02,
      double m03,
      double m10,
      double m11,
      double m12,
      double m13,
      double m20,
      double m21,
      double m22,
      double m23,
      double m30,
      double m31,
      double m32,
      double m33) {
    this.m[0] = m00;
    this.m[1] = m01;
    this.m[2] = m02;
    this.m[3] = m03;
    this.m[4] = m10;
    this.m[5] = m11;
    this.m[6] = m12;
    this.m[7] = m13;
    this.m[8] = m20;
    this.m[9] = m21;
    this.m[10] = m22;
    this.m[11] = m23;
    this.m[12] = m30;
    this.m[13] = m31;
    this.m[14] = m32;
    this.m[15] = m33;
    updateTransformValues();
    return this;
  }

  public Matrix4 set(Matrix4 other) {
    System.arraycopy(other.m, 0, this.m, 0, 16);
    this.tx = other.tx;
    this.ty = other.ty;
    this.tz = other.tz;
    this.rx = other.rx;
    this.ry = other.ry;
    this.rz = other.rz;
    this.sx = other.sx;
    this.sy = other.sy;
    this.sz = other.sz;
    return this;
  }

  public Matrix4 setElement(int row, int col, double value) {
    if (row < 0 || row >= 4 || col < 0 || col >= 4) {
      throw new IndexOutOfBoundsException(
          "Matrix4 indices out of bounds: [" + row + "][" + col + "]");
    }
    this.m[row * 4 + col] = value;
    updateTransformValues();
    return this;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double get(int row, int col) {
    if (row < 0 || row >= 4 || col < 0 || col >= 4) {
      throw new IndexOutOfBoundsException(
          "Matrix4 indices out of bounds: [" + row + "][" + col + "]");
    }
    return m[row * 4 + col];
  }

  /**
   * Returns the internal backing array directly (no copy, no allocation). Callers must not resize
   * it; mutating its contents mutates this matrix.
   */
  public double[] getElements() {
    return this.m;
  }

  public double getTx() {
    return tx;
  }

  public double getTy() {
    return ty;
  }

  public double getTz() {
    return tz;
  }

  public double getRx() {
    return rx;
  }

  public double getRy() {
    return ry;
  }

  public double getRz() {
    return rz;
  }

  public double getSx() {
    return sx;
  }

  public double getSy() {
    return sy;
  }

  public double getSz() {
    return sz;
  }

  public double getScaleX() {
    return Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]);
  }

  public double getScaleY() {
    return Math.sqrt(m[1] * m[1] + m[5] * m[5] + m[9] * m[9]);
  }

  public double getScaleZ() {
    return Math.sqrt(m[2] * m[2] + m[6] * m[6] + m[10] * m[10]);
  }

  // =========================================================================
  // TRANSFORM METHODS - GC-FRIENDLY (target parameter)
  // =========================================================================

  public Point3 transformPoint(Point3 source, Point3 target) {
    double x = source.x;
    double y = source.y;
    double z = source.z;
    target.x = m[0] * x + m[1] * y + m[2] * z + m[3];
    target.y = m[4] * x + m[5] * y + m[6] * z + m[7];
    target.z = m[8] * x + m[9] * y + m[10] * z + m[11];
    return target;
  }

  public Point3 transformPoint(Point3 point) {
    double x = point.x;
    double y = point.y;
    double z = point.z;
    point.x = m[0] * x + m[1] * y + m[2] * z + m[3];
    point.y = m[4] * x + m[5] * y + m[6] * z + m[7];
    point.z = m[8] * x + m[9] * y + m[10] * z + m[11];
    return point;
  }

  public Vector3 transformDirection(Vector3 source, Vector3 target) {
    double x = source.x;
    double y = source.y;
    double z = source.z;
    target.x = m[0] * x + m[1] * y + m[2] * z;
    target.y = m[4] * x + m[5] * y + m[6] * z;
    target.z = m[8] * x + m[9] * y + m[10] * z;
    return target;
  }

  public Vector3 transformDirection(Vector3 vector) {
    double x = vector.x;
    double y = vector.y;
    double z = vector.z;
    vector.x = m[0] * x + m[1] * y + m[2] * z;
    vector.y = m[4] * x + m[5] * y + m[6] * z;
    vector.z = m[8] * x + m[9] * y + m[10] * z;
    return vector;
  }

  // ALIAS for transformDirection (for compatibility)
  public Vector3 transformVector(Vector3 vector) {
    return transformDirection(vector);
  }

  public Vector3 transformVector(Vector3 source, Vector3 target) {
    return transformDirection(source, target);
  }

  public Vector3 transformNormal(Vector3 source, Vector3 target) {
    scratchMatrix3_1.set(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]);

    if (!scratchMatrix3_1.inverse(scratchMatrix3_2)) {
      return target.set(0.0, 0.0, 0.0);
    }

    scratchMatrix3_2.transpose(scratchMatrix3_3);

    double x = source.x;
    double y = source.y;
    double z = source.z;
    target.x =
        scratchMatrix3_3.get(0, 0) * x
            + scratchMatrix3_3.get(0, 1) * y
            + scratchMatrix3_3.get(0, 2) * z;
    target.y =
        scratchMatrix3_3.get(1, 0) * x
            + scratchMatrix3_3.get(1, 1) * y
            + scratchMatrix3_3.get(1, 2) * z;
    target.z =
        scratchMatrix3_3.get(2, 0) * x
            + scratchMatrix3_3.get(2, 1) * y
            + scratchMatrix3_3.get(2, 2) * z;
    target.normalize();

    return target;
  }

  public Vector3 transformNormal(Vector3 normal) {
    scratchMatrix3_1.set(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]);

    if (!scratchMatrix3_1.inverse(scratchMatrix3_2)) {
      normal.set(0.0, 0.0, 0.0);
      return normal;
    }

    scratchMatrix3_2.transpose(scratchMatrix3_3);

    double x = normal.x;
    double y = normal.y;
    double z = normal.z;
    normal.x =
        scratchMatrix3_3.get(0, 0) * x
            + scratchMatrix3_3.get(0, 1) * y
            + scratchMatrix3_3.get(0, 2) * z;
    normal.y =
        scratchMatrix3_3.get(1, 0) * x
            + scratchMatrix3_3.get(1, 1) * y
            + scratchMatrix3_3.get(1, 2) * z;
    normal.z =
        scratchMatrix3_3.get(2, 0) * x
            + scratchMatrix3_3.get(2, 1) * y
            + scratchMatrix3_3.get(2, 2) * z;
    normal.normalize();

    return normal;
  }

  public void transformNormal(double[] n) {
    double x = n[0], y = n[1], z = n[2];
    n[0] = m[0] * x + m[4] * y + m[8] * z;
    n[1] = m[1] * x + m[5] * y + m[9] * z;
    n[2] = m[2] * x + m[6] * y + m[10] * z;
  }

  public void transformNormalAndNormalize(double[] n) {
    double x = n[0], y = n[1], z = n[2];
    n[0] = m[0] * x + m[4] * y + m[8] * z;
    n[1] = m[1] * x + m[5] * y + m[9] * z;
    n[2] = m[2] * x + m[6] * y + m[10] * z;

    double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
    if (len > 1e-10) {
      n[0] /= len;
      n[1] /= len;
      n[2] /= len;
    }
  }

  /** Transforms a ray from source into target (mutates target only). No allocation. */
  public Ray transformRay(Ray source, Ray target) {
    transformPoint(source.getOrigin(), target.getOrigin());
    transformDirection(source.getDirection(), target.getDirection());
    target.getDirection().normalize();
    target.setEnergy(source.getEnergy());
    return target;
  }

  /**
   * Transforms a ray in place. Previously allocated a fresh Point3 and Vector3 on every call; now
   * reuses per-instance scratch fields ({@code scratchRayOrigin} / {@code scratchRayDirection})
   * instead. No allocation.
   *
   * <p>Not reentrant/thread-safe on the same Matrix4 instance, consistent with the rest of this
   * class's scratch-field usage.
   */
  public Ray transformRay(Ray ray) {
    transformPoint(ray.getOrigin(), scratchRayOrigin);
    transformDirection(ray.getDirection(), scratchRayDirection);
    scratchRayDirection.normalize();
    ray.set(scratchRayOrigin, scratchRayDirection, ray.getEnergy());
    return ray;
  }

  // =========================================================================
  // MATRIX OPERATIONS
  // =========================================================================

  /**
   * Multiplies this matrix by other and stores the result in target (does not mutate this or
   * other). No heap allocation - uses 16 local stack variables instead of a temporary array.
   */
  public Matrix4 multiply(Matrix4 other, Matrix4 target) {
    double[] a = this.m;
    double[] b = other.m;

    double r00 = a[0] * b[0] + a[1] * b[4] + a[2] * b[8] + a[3] * b[12];
    double r01 = a[0] * b[1] + a[1] * b[5] + a[2] * b[9] + a[3] * b[13];
    double r02 = a[0] * b[2] + a[1] * b[6] + a[2] * b[10] + a[3] * b[14];
    double r03 = a[0] * b[3] + a[1] * b[7] + a[2] * b[11] + a[3] * b[15];

    double r10 = a[4] * b[0] + a[5] * b[4] + a[6] * b[8] + a[7] * b[12];
    double r11 = a[4] * b[1] + a[5] * b[5] + a[6] * b[9] + a[7] * b[13];
    double r12 = a[4] * b[2] + a[5] * b[6] + a[6] * b[10] + a[7] * b[14];
    double r13 = a[4] * b[3] + a[5] * b[7] + a[6] * b[11] + a[7] * b[15];

    double r20 = a[8] * b[0] + a[9] * b[4] + a[10] * b[8] + a[11] * b[12];
    double r21 = a[8] * b[1] + a[9] * b[5] + a[10] * b[9] + a[11] * b[13];
    double r22 = a[8] * b[2] + a[9] * b[6] + a[10] * b[10] + a[11] * b[14];
    double r23 = a[8] * b[3] + a[9] * b[7] + a[10] * b[11] + a[11] * b[15];

    double r30 = a[12] * b[0] + a[13] * b[4] + a[14] * b[8] + a[15] * b[12];
    double r31 = a[12] * b[1] + a[13] * b[5] + a[14] * b[9] + a[15] * b[13];
    double r32 = a[12] * b[2] + a[13] * b[6] + a[14] * b[10] + a[15] * b[14];
    double r33 = a[12] * b[3] + a[13] * b[7] + a[14] * b[11] + a[15] * b[15];

    target.set(r00, r01, r02, r03, r10, r11, r12, r13, r20, r21, r22, r23, r30, r31, r32, r33);
    return target;
  }

  /**
   * Multiplies this matrix by other in place (mutates this). Previously allocated a temporary
   * {@code double[16]}; now computes into 16 local stack variables before writing back. No heap
   * allocation.
   */
  public Matrix4 multiply(Matrix4 other) {
    double[] a = this.m;
    double[] b = other.m;

    double r00 = a[0] * b[0] + a[1] * b[4] + a[2] * b[8] + a[3] * b[12];
    double r01 = a[0] * b[1] + a[1] * b[5] + a[2] * b[9] + a[3] * b[13];
    double r02 = a[0] * b[2] + a[1] * b[6] + a[2] * b[10] + a[3] * b[14];
    double r03 = a[0] * b[3] + a[1] * b[7] + a[2] * b[11] + a[3] * b[15];

    double r10 = a[4] * b[0] + a[5] * b[4] + a[6] * b[8] + a[7] * b[12];
    double r11 = a[4] * b[1] + a[5] * b[5] + a[6] * b[9] + a[7] * b[13];
    double r12 = a[4] * b[2] + a[5] * b[6] + a[6] * b[10] + a[7] * b[14];
    double r13 = a[4] * b[3] + a[5] * b[7] + a[6] * b[11] + a[7] * b[15];

    double r20 = a[8] * b[0] + a[9] * b[4] + a[10] * b[8] + a[11] * b[12];
    double r21 = a[8] * b[1] + a[9] * b[5] + a[10] * b[9] + a[11] * b[13];
    double r22 = a[8] * b[2] + a[9] * b[6] + a[10] * b[10] + a[11] * b[14];
    double r23 = a[8] * b[3] + a[9] * b[7] + a[10] * b[11] + a[11] * b[15];

    double r30 = a[12] * b[0] + a[13] * b[4] + a[14] * b[8] + a[15] * b[12];
    double r31 = a[12] * b[1] + a[13] * b[5] + a[14] * b[9] + a[15] * b[13];
    double r32 = a[12] * b[2] + a[13] * b[6] + a[14] * b[10] + a[15] * b[14];
    double r33 = a[12] * b[3] + a[13] * b[7] + a[14] * b[11] + a[15] * b[15];

    m[0] = r00;
    m[1] = r01;
    m[2] = r02;
    m[3] = r03;
    m[4] = r10;
    m[5] = r11;
    m[6] = r12;
    m[7] = r13;
    m[8] = r20;
    m[9] = r21;
    m[10] = r22;
    m[11] = r23;
    m[12] = r30;
    m[13] = r31;
    m[14] = r32;
    m[15] = r33;

    updateTransformValues();
    return this;
  }

  public Matrix4 multiply(double scalar) {
    for (int i = 0; i < 16; i++) {
      this.m[i] *= scalar;
    }
    updateTransformValues();
    return this;
  }

  public Matrix4 transpose() {
    double temp;
    temp = m[1];
    m[1] = m[4];
    m[4] = temp;
    temp = m[2];
    m[2] = m[8];
    m[8] = temp;
    temp = m[3];
    m[3] = m[12];
    m[12] = temp;
    temp = m[6];
    m[6] = m[9];
    m[9] = temp;
    temp = m[7];
    m[7] = m[13];
    m[13] = temp;
    temp = m[11];
    m[11] = m[14];
    m[14] = temp;
    updateTransformValues();
    return this;
  }

  public Matrix4 transpose(Matrix4 target) {
    target.set(
        m[0], m[4], m[8], m[12], m[1], m[5], m[9], m[13], m[2], m[6], m[10], m[14], m[3], m[7],
        m[11], m[15]);
    return target;
  }

  // =========================================================================
  // INVERSE METHODS - BOTH VERSIONS (for compatibility)
  // =========================================================================

  /**
   * Computes the inverse (mutates target). No allocation - the translation/inverse-translation
   * vectors used internally are per-instance scratch fields ({@code scratchTranslation} / {@code
   * scratchInvTranslation}) rather than freshly allocated Vector3 instances.
   *
   * @return true if invertible, false otherwise
   */
  public boolean inverse(Matrix4 target) {
    scratchMatrix3_1.set(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]);

    if (!scratchMatrix3_1.inverse(scratchMatrix3_2)) {
      return false;
    }

    target.m[0] = scratchMatrix3_2.get(0, 0);
    target.m[1] = scratchMatrix3_2.get(0, 1);
    target.m[2] = scratchMatrix3_2.get(0, 2);
    target.m[4] = scratchMatrix3_2.get(1, 0);
    target.m[5] = scratchMatrix3_2.get(1, 1);
    target.m[6] = scratchMatrix3_2.get(1, 2);
    target.m[8] = scratchMatrix3_2.get(2, 0);
    target.m[9] = scratchMatrix3_2.get(2, 1);
    target.m[10] = scratchMatrix3_2.get(2, 2);

    scratchTranslation.set(m[3], m[7], m[11]);
    scratchMatrix3_2.transform(scratchTranslation, scratchInvTranslation);
    scratchInvTranslation.negate();

    target.m[3] = scratchInvTranslation.x;
    target.m[7] = scratchInvTranslation.y;
    target.m[11] = scratchInvTranslation.z;
    target.m[12] = 0.0;
    target.m[13] = 0.0;
    target.m[14] = 0.0;
    target.m[15] = 1.0;

    target.updateTransformValues();
    return true;
  }

  /**
   * Computes inverse (allocates a new Matrix4).
   *
   * <p><b>ALLOCATES</b> - signature has no target parameter, so one Matrix4 allocation is
   * unavoidable here. Use {@link #inverse(Matrix4)} in any hot path.
   */
  public Matrix4 inverse() {
    Matrix4 result = new Matrix4();
    if (inverse(result)) {
      return result;
    }
    return null;
  }

  // =========================================================================
  // INVERSE TRANSPOSE METHODS - BOTH VERSIONS (for compatibility)
  // =========================================================================

  /**
   * Computes inverse transpose for normal transformation (mutates target). No allocation.
   *
   * @return target for method chaining, null if non-invertible
   */
  public Matrix4 inverseTransposeForNormal(Matrix4 target) {
    scratchMatrix3_1.set(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]);

    if (!scratchMatrix3_1.inverse(scratchMatrix3_2)) {
      return null;
    }

    scratchMatrix3_2.transpose(scratchMatrix3_3);

    target.m[0] = scratchMatrix3_3.get(0, 0);
    target.m[1] = scratchMatrix3_3.get(0, 1);
    target.m[2] = scratchMatrix3_3.get(0, 2);
    target.m[3] = 0.0;
    target.m[4] = scratchMatrix3_3.get(1, 0);
    target.m[5] = scratchMatrix3_3.get(1, 1);
    target.m[6] = scratchMatrix3_3.get(1, 2);
    target.m[7] = 0.0;
    target.m[8] = scratchMatrix3_3.get(2, 0);
    target.m[9] = scratchMatrix3_3.get(2, 1);
    target.m[10] = scratchMatrix3_3.get(2, 2);
    target.m[11] = 0.0;
    target.m[12] = 0.0;
    target.m[13] = 0.0;
    target.m[14] = 0.0;
    target.m[15] = 1.0;

    target.updateTransformValues();
    return target;
  }

  /**
   * Computes inverse transpose for normal transformation (allocates a new Matrix4).
   *
   * <p><b>ALLOCATES</b> - signature has no target parameter, so one Matrix4 allocation is
   * unavoidable here. Use {@link #inverseTransposeForNormal(Matrix4)} in any hot path.
   */
  public Matrix4 inverseTransposeForNormal() {
    Matrix4 result = new Matrix4();
    return inverseTransposeForNormal(result);
  }

  // =========================================================================
  // STATIC FACTORY METHODS
  // =========================================================================
  // All of the following ALLOCATE a new Matrix4: they are static factory
  // / construction helpers with no target parameter in their signature,
  // intended for one-time scene setup (building transforms), not for
  // per-pixel or per-ray hot paths.

  /** ALLOCATES a new Matrix4 (identity). */
  public static Matrix4 identity() {
    return new Matrix4();
  }

  /** ALLOCATES a new Matrix4 (translation). */
  public static Matrix4 translate(Vector3 translation) {
    return new Matrix4(
        1, 0, 0, translation.x, 0, 1, 0, translation.y, 0, 0, 1, translation.z, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 (translation). */
  public static Matrix4 translate(double x, double y, double z) {
    return new Matrix4(1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 (rotation around X). */
  public static Matrix4 rotateX(double angleDegrees) {
    double angleRad = Math.toRadians(angleDegrees);
    double cosA = Math.cos(angleRad);
    double sinA = Math.sin(angleRad);
    return new Matrix4(1, 0, 0, 0, 0, cosA, -sinA, 0, 0, sinA, cosA, 0, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 (rotation around Y). */
  public static Matrix4 rotateY(double angleDegrees) {
    double angleRad = Math.toRadians(angleDegrees);
    double cosA = Math.cos(angleRad);
    double sinA = Math.sin(angleRad);
    return new Matrix4(cosA, 0, sinA, 0, 0, 1, 0, 0, -sinA, 0, cosA, 0, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 (rotation around Z). */
  public static Matrix4 rotateZ(double angleDegrees) {
    double angleRad = Math.toRadians(angleDegrees);
    double cosA = Math.cos(angleRad);
    double sinA = Math.sin(angleRad);
    return new Matrix4(cosA, -sinA, 0, 0, sinA, cosA, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 (scale). */
  public static Matrix4 scale(double sx, double sy, double sz) {
    return new Matrix4(sx, 0, 0, 0, 0, sy, 0, 0, 0, 0, sz, 0, 0, 0, 0, 1);
  }

  /** ALLOCATES a new Matrix4 built from a Matrix3 (rotation/scale block only). */
  public static Matrix4 fromMatrix3(Matrix3 m3) {
    return new Matrix4(
        m3.get(0, 0),
        m3.get(0, 1),
        m3.get(0, 2),
        0,
        m3.get(1, 0),
        m3.get(1, 1),
        m3.get(1, 2),
        0,
        m3.get(2, 0),
        m3.get(2, 1),
        m3.get(2, 2),
        0,
        0,
        0,
        0,
        1);
  }

  /**
   * Combines two TRS matrices by summing their translation/rotation/scale components and rebuilding
   * a composite matrix.
   *
   * <p><b>ALLOCATES</b> several intermediate Matrix4 instances (translation, three rotation
   * matrices, combined rotation, scale, and the final product). This is a scene-setup /
   * animation-blending helper, not intended for per-pixel or per-ray hot paths.
   */
  public static Matrix4 add(Matrix4 trsA, Matrix4 trsB) {
    if (trsA == null || trsB == null) {
      throw new IllegalArgumentException("Both matrices must be non-null");
    }

    double sumTx = trsA.getTx() + trsB.getTx();
    double sumTy = trsA.getTy() + trsB.getTy();
    double sumTz = trsA.getTz() + trsB.getTz();

    double sumRx = trsA.getRx() + trsB.getRx();
    double sumRy = trsA.getRy() + trsB.getRy();
    double sumRz = trsA.getRz() + trsB.getRz();

    double sumSx = (trsA.getSx() + trsB.getSx()) - 1;
    double sumSy = (trsA.getSy() + trsB.getSy()) - 1;
    double sumSz = (trsA.getSz() + trsB.getSz()) - 1;

    Matrix4 translation = Matrix4.translate(sumTx, sumTy, sumTz);
    Matrix4 rotationZ = Matrix4.rotateZ(sumRz);
    Matrix4 rotationY = Matrix4.rotateY(sumRy);
    Matrix4 rotationX = Matrix4.rotateX(sumRx);
    Matrix4 rotation = rotationZ.multiply(rotationY).multiply(rotationX);
    Matrix4 scale = Matrix4.scale(sumSx, sumSy, sumSz);

    return translation.multiply(rotation).multiply(scale);
  }

  // =========================================================================
  // PARSING METHODS
  // =========================================================================
  // Config/scene-file parsing helpers. Not hot-path code: allocation here
  // (String[] from split(), intermediate Matrix4 instances) is expected
  // and left as-is.

  public static Matrix4 createMatrixFromString(String transformString) {
    if (transformString == null || transformString.trim().isEmpty()) {
      throw new IllegalArgumentException("Transformation string cannot be null or empty");
    }

    String cleanString = transformString.trim();
    if (cleanString.startsWith("transform = ")) {
      cleanString = cleanString.substring("transform = ".length());
    } else if (cleanString.startsWith("animTransform = ")) {
      cleanString = cleanString.substring("animTransform = ".length());
    } else if (cleanString.startsWith("firstAnim_transform = ")) {
      cleanString = cleanString.substring("firstAnim_transform = ".length());
    } else if (cleanString.startsWith("secondAnim_transform = ")) {
      cleanString = cleanString.substring("secondAnim_transform = ".length());
    } else if (cleanString.startsWith("firstAnimMatrix = ")) {
      cleanString = cleanString.substring("firstAnimMatrix = ".length());
    } else if (cleanString.startsWith("secondAnimMatrix = ")) {
      cleanString = cleanString.substring("secondAnimMatrix = ".length());
    }

    if (cleanString.endsWith(";")) {
      cleanString = cleanString.substring(0, cleanString.length() - 1);
    }

    String[] transformations = cleanString.split("\\s*\\*\\s*");
    if (transformations.length != 3) {
      throw new IllegalArgumentException(
          "Expected exactly 3 transformations (translate, rotate, scale)");
    }

    Matrix4 translationMatrix = parseTranslation(transformations[0]);
    Matrix4 rotationMatrix = parseRotation(transformations[1]);
    Matrix4 scaleMatrix = parseScale(transformations[2]);

    return translationMatrix.multiply(rotationMatrix).multiply(scaleMatrix);
  }

  private static Matrix4 parseTranslation(String translationStr) {
    if (!translationStr.startsWith("translate(") || !translationStr.endsWith(")")) {
      throw new IllegalArgumentException("Invalid translation format: " + translationStr);
    }

    String content = translationStr.substring("translate(".length(), translationStr.length() - 1);
    String[] components = content.split("\\s*,\\s*");
    if (components.length != 3) {
      throw new IllegalArgumentException(
          "Translation requires exactly 3 components: " + translationStr);
    }

    try {
      double x = Double.parseDouble(components[0]);
      double y = Double.parseDouble(components[1]);
      double z = Double.parseDouble(components[2]);
      return Matrix4.translate(x, y, z);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid number in translation: " + translationStr, e);
    }
  }

  private static Matrix4 parseRotation(String rotationStr) {
    if (!rotationStr.startsWith("rotate(") || !rotationStr.endsWith(")")) {
      throw new IllegalArgumentException("Invalid rotation format: " + rotationStr);
    }

    String content = rotationStr.substring("rotate(".length(), rotationStr.length() - 1);
    String[] components = content.split("\\s*,\\s*");
    if (components.length != 3) {
      throw new IllegalArgumentException("Rotation requires exactly 3 components: " + rotationStr);
    }

    try {
      double rx = Double.parseDouble(components[0]);
      double ry = Double.parseDouble(components[1]);
      double rz = Double.parseDouble(components[2]);

      Matrix4 rotX = Matrix4.rotateX(rx);
      Matrix4 rotY = Matrix4.rotateY(ry);
      Matrix4 rotZ = Matrix4.rotateZ(rz);

      return rotZ.multiply(rotY).multiply(rotX);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid number in rotation: " + rotationStr, e);
    }
  }

  private static Matrix4 parseScale(String scaleStr) {
    if (!scaleStr.startsWith("scale(") || !scaleStr.endsWith(")")) {
      throw new IllegalArgumentException("Invalid scale format: " + scaleStr);
    }

    String content = scaleStr.substring("scale(".length(), scaleStr.length() - 1);
    String[] components = content.split("\\s*,\\s*");
    if (components.length != 3) {
      throw new IllegalArgumentException("Scale requires exactly 3 components: " + scaleStr);
    }

    try {
      double sx = Double.parseDouble(components[0]);
      double sy = Double.parseDouble(components[1]);
      double sz = Double.parseDouble(components[2]);
      return Matrix4.scale(sx, sy, sz);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid number in scale: " + scaleStr, e);
    }
  }

  // =========================================================================
  // SETTERS (Transform values)
  // =========================================================================

  public void setTx(double t) {
    this.tx = t;
  }

  public void setTy(double t) {
    this.ty = t;
  }

  public void setTz(double t) {
    this.tz = t;
  }

  public void setRx(double t) {
    this.rx = t;
  }

  public void setRy(double t) {
    this.ry = t;
  }

  public void setRz(double t) {
    this.rz = t;
  }

  public void setSx(double t) {
    this.sx = t;
  }

  public void setSy(double t) {
    this.sy = t;
  }

  public void setSz(double t) {
    this.sz = t;
  }

  /**
   * public void setSx(double t) { this.sx = t; double currentScale = Math.sqrt(m[0]*m[0] +
   * m[4]*m[4] + m[8]*m[8]); if (currentScale > 0.0001) { double ratio = t / currentScale; m[0] *=
   * ratio; m[4] *= ratio; m[8] *= ratio; } updateTransformValues(); }
   *
   * <p>public void setSy(double t) { this.sy = t; double currentScale = Math.sqrt(m[1]*m[1] +
   * m[5]*m[5] + m[9]*m[9]); if (currentScale > 0.0001) { double ratio = t / currentScale; m[1] *=
   * ratio; m[5] *= ratio; m[9] *= ratio; } updateTransformValues(); }
   *
   * <p>public void setSz(double t) { this.sz = t; double currentScale = Math.sqrt(m[2]*m[2] +
   * m[6]*m[6] + m[10]*m[10]); if (currentScale > 0.0001) { double ratio = t / currentScale; m[2] *=
   * ratio; m[6] *= ratio; m[10] *= ratio; } updateTransformValues(); }
   */

  // =========================================================================
  // PRIVATE HELPERS
  // =========================================================================

  /**
   * private void updateTransformValues() { this.tx = m[3]; this.ty = m[7]; this.tz = m[11]; this.sx
   * = getScaleX(); this.sy = getScaleY(); this.sz = getScaleZ();
   *
   * <p>if (sx > 0 && sy > 0 && sz > 0) { this.ry = Math.toDegrees(Math.asin(-m[8] / sx)); this.rx =
   * Math.toDegrees(Math.atan2(m[9] / sy, m[10] / sz)); this.rz = Math.toDegrees(Math.atan2(m[4] /
   * sx, m[0] / sx)); } }
   */

  /**
   * private void updateTransformValues() { this.tx = m[3]; this.ty = m[7]; this.tz = m[11];
   *
   * <p>this.sx = Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]); this.sy = Math.sqrt(m[1] *
   * m[1] + m[5] * m[5] + m[9] * m[9]); this.sz = Math.sqrt(m[2] * m[2] + m[6] * m[6] + m[10] *
   * m[10]);
   *
   * <p>if (sx > 0.0001 && sy > 0.0001 && sz > 0.0001) { double m00 = m[0] / sx; double m10 = m[4] /
   * sy; double m20 = m[8] / sz; double m21 = m[9] / sy; double m22 = m[10] / sz;
   *
   * <p>this.ry = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -m20)))); this.rx =
   * Math.toDegrees(Math.atan2(m21, m22)); this.rz = Math.toDegrees(Math.atan2(m10, m00)); } }
   */
  private void updateTransformValues() {
    this.tx = m[3];
    this.ty = m[7];
    this.tz = m[11];

    this.sx = Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]);
    this.sy = Math.sqrt(m[1] * m[1] + m[5] * m[5] + m[9] * m[9]);
    this.sz = Math.sqrt(m[2] * m[2] + m[6] * m[6] + m[10] * m[10]);

    if (sx > 0.0001 && sy > 0.0001 && sz > 0.0001) {
      double m00 = m[0] / sx;
      double m10 = m[4] / sx;
      double m20 = m[8] / sx;
      double m21 = m[9] / sy;
      double m22 = m[10] / sz;

      this.ry = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -m20))));
      this.rx = Math.toDegrees(Math.atan2(m21, m22));
      this.rz = Math.toDegrees(Math.atan2(m10, m00));
    }
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  public String toValuesString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      sb.append("| ");
      for (int j = 0; j < 4; j++) {
        sb.append(String.format(java.util.Locale.US, "%8.4f", m[i * 4 + j])).append(" ");
      }
      sb.append("|\n");
    }
    return sb.toString();
  }

  public String toBracketString() {
    return String.format(
        java.util.Locale.US,
        "[%.2f %.2f %.2f]*[%.2f %.2f %.2f]*[%.2f %.2f %.2f]",
        tx,
        ty,
        tz,
        rx,
        ry,
        rz,
        sx,
        sy,
        sz);
  }

  @Override
  public String toString() {
    return String.format(
        java.util.Locale.US,
        "transform = translate(%.2f, %.2f, %.2f) * rotate(%.2f, %.2f, %.2f) * scale(%.2f, %.2f,"
            + " %.2f);",
        tx,
        ty,
        tz,
        rx,
        ry,
        rz,
        sx,
        sy,
        sz);
  }
}
