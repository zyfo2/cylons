package org.rawkintrevo.cylon.localengine

import org.apache.mahout.math.{DenseVector, Matrix, Vector}
import org.opencv.core.{Core, Mat, Size}
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.rawkintrevo.cylon.common.mahout.MahoutUtils
import org.rawkintrevo.cylon.frameprocessors.{FaceDetectorProcessor, ImageUtils}

trait AbstractFaceDecomposer extends AbstractLocalEngine {

  var eigenfacesInCore: Matrix = _
  var colCentersV: Vector = _
  var cascadeFilterPath: String = _

  def writeOutput(vec: Vector)

  def loadEigenFacesAndColCenters(efPath: String, ccPath: String): Unit = {
    logger.info(s"Loading Eigenfaces from ${efPath}")
    eigenfacesInCore = MahoutUtils.matrixReader(efPath)
    val efRows = eigenfacesInCore.numRows()
    val efCols = eigenfacesInCore.numCols()
    logger.info(s"Loaded Eigenfaces matrix ${efRows}x${efCols}")

    colCentersV = MahoutUtils.vectorReader(ccPath)
  }

  def run() = {
    //System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
    Class.forName("org.rawkintrevo.cylon.common.opencv.LoadNative")

    val videoCapture = new VideoCapture
    logger.info(s"Attempting to open video source at ${inputPath}")
    videoCapture.open(inputPath)

    if (!videoCapture.isOpened) logger.warn("Camera Error")
    else logger.info(s"Successfully opened video source at ${inputPath}")

    // Create Cascade Filter /////////////////////////////////////////////////////////////////////////////////////////
    FaceDetectorProcessor.initCascadeClassifier(cascadeFilterPath)

    // Init variables needed /////////////////////////////////////////////////////////////////////////////////////////
    var mat = new Mat()

    while (videoCapture.read(mat)) {
      val faceRects = FaceDetectorProcessor.createFaceRects(mat)

      val faceArray = faceRects.toArray

      // Scale faces to 250x250 and convert to Mahout DenseVector
      val faceVecArray: Array[DenseVector] = faceArray.map(r => {
        val faceMat = new Mat(mat, r)
        val size: Size = new Size(250, 250)
        val resizeMat = new Mat(size, faceMat.`type`())
        Imgproc.resize(faceMat, resizeMat, size)
        val faceVec = new DenseVector(ImageUtils.matToPixelArray(ImageUtils.grayAndEqualizeMat(resizeMat)))
        faceVec
      })

      // Decompose Image into linear combo of eigenfaces (which were calulated offline)
      val faceDecompVecArray: Array[Vector] = faceVecArray
        .map(v => MahoutUtils.decomposeImgVecWithEigenfaces(v, eigenfacesInCore).minus(colCentersV))

      for (vec <- faceDecompVecArray) {
        writeOutput(vec)
      }
    }

  }

}