package morphinator

import chisel3._
import chisel3.util._

class Filter(val imageWidth: Int, val imageHeight: Int, val parallelPixels: Int, val kernelSize: Int) extends Module {

    val io = IO(
        new Bundle {
            // input fra SPI
            val SPI_filterIndex = Input(UInt(6.W))
            val SPI_invert      = Input(Bool())
            val SPI_distort     = Input(Bool())
            
            val pixelVal_out    = Output(Vec(3, Vec(parallelPixels, UInt(4.W))))
            val valid_out       = Output(Bool())
        }
    )

    val greyScale = io.SPI_distort //TODO make names consistant

    val colorInvert = io.SPI_invert 
  
    val kernels = VecInit(
        VecInit( // Identity
            0.S(5.W), 0.S(5.W), 0.S(5.W),
            0.S(5.W), 1.S(5.W), 0.S(5.W),
            0.S(5.W), 0.S(5.W), 0.S(5.W)
        ),
        VecInit( // Box blur
            1.S(5.W), 1.S(5.W), 1.S(5.W),
            1.S(5.W), 1.S(5.W), 1.S(5.W),
            1.S(5.W), 1.S(5.W), 1.S(5.W)
        ),
        VecInit( // Gaussian blur
            1.S(5.W), 2.S(5.W), 1.S(5.W),
            2.S(5.W), 4.S(5.W), 2.S(5.W),
            1.S(5.W), 2.S(5.W), 1.S(5.W)
        ),
        VecInit( // Edge detector
            0.S(5.W), -1.S(5.W), 0.S(5.W),
            -1.S(5.W), 4.S(5.W), -1.S(5.W),
            0.S(5.W), -1.S(5.W), 0.S(5.W)
        ),
        VecInit( // Edge detector
            -1.S(5.W), -1.S(5.W), -1.S(5.W),
            -1.S(5.W), 8.S(5.W), -1.S(5.W),
            -1.S(5.W), -1.S(5.W), -1.S(5.W)
        ),
        VecInit( // Sharpening
            0.S(5.W), -1.S(5.W), 0.S(5.W),
            -1.S(5.W), 5.S(5.W), -1.S(5.W),
            0.S(5.W), -1.S(5.W), 0.S(5.W)
        )
    )
    val kernelSums = VecInit(
        RegInit(SInt(8.W), 1.S),
        RegInit(SInt(8.W), 9.S),
        RegInit(SInt(8.W), 16.S),
        RegInit(SInt(8.W), 1.S),
        RegInit(SInt(8.W), 1.S),
        RegInit(SInt(8.W), 1.S)
    )
  
    // poggers
    // val image = VecInit(
    //   0.U(4.W),0.U(4.W),5.U(4.W),6.U(4.W),5.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),4.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),
    //   0.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),7.U(4.W),10.U(4.W),11.U(4.W),9.U(4.W),6.U(4.W),6.U(4.W),10.U(4.W),13.U(4.W),12.U(4.W),10.U(4.W),1.U(4.W),
    //   4.U(4.W),6.U(4.W),6.U(4.W),7.U(4.W),11.U(4.W),14.U(4.W),15.U(4.W),12.U(4.W),7.U(4.W),8.U(4.W),12.U(4.W),15.U(4.W),15.U(4.W),6.U(4.W),8.U(4.W),11.U(4.W),
    //   5.U(4.W),6.U(4.W),6.U(4.W),8.U(4.W),12.U(4.W),15.U(4.W),15.U(4.W),12.U(4.W),5.U(4.W),8.U(4.W),11.U(4.W),13.U(4.W),14.U(4.W),11.U(4.W),9.U(4.W),12.U(4.W),
    //   4.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),8.U(4.W),11.U(4.W),13.U(4.W),11.U(4.W),10.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),7.U(4.W),4.U(4.W),4.U(4.W),
    //   4.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),7.U(4.W),6.U(4.W),4.U(4.W),
    //   4.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),3.U(4.W),
    //   5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),4.U(4.W),3.U(4.W),4.U(4.W),4.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),0.U(4.W),
    //   4.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),4.U(4.W),4.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),0.U(4.W),
    //   0.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),0.U(4.W),
    //   0.U(4.W),4.U(4.W),4.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),0.U(4.W),
    //   0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),5.U(4.W),6.U(4.W),5.U(4.W),6.U(4.W),6.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),5.U(4.W),0.U(4.W),0.U(4.W),
    // )
    //
    // squares
    // val image = VecInit(
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),4.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),8.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    // )
    //
    // squares rgb
    // val imageB = VecInit(
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    // )
    // val imageG = VecInit(
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    //     0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    // )
    // val imageR = VecInit(
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    //     15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        // 15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        // 15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        // 15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
        // 0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),15.U(4.W),
    // )

    val imageB = VecInit(
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),2.U(4.W),2.U(4.W),2.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),3.U(4.W),6.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),4.U(4.W),11.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),3.U(4.W),3.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),13.U(4.W),3.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),14.U(4.W),6.U(4.W),0.U(4.W),6.U(4.W),3.U(4.W),4.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),6.U(4.W),6.U(4.W),14.U(4.W),10.U(4.W),6.U(4.W),10.U(4.W),14.U(4.W),3.U(4.W),3.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),1.U(4.W),3.U(4.W),14.U(4.W),14.U(4.W),0.U(4.W),14.U(4.W),14.U(4.W),3.U(4.W),1.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),4.U(4.W),3.U(4.W),4.U(4.W),3.U(4.W),0.U(4.W),3.U(4.W),4.U(4.W),3.U(4.W),4.U(4.W),3.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),8.U(4.W),12.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),2.U(4.W),3.U(4.W),2.U(4.W),0.U(4.W),12.U(4.W),8.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),6.U(4.W),0.U(4.W),11.U(4.W),1.U(4.W),4.U(4.W),1.U(4.W),11.U(4.W),0.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    )
    val imageG = VecInit(
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),6.U(4.W),6.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),1.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),6.U(4.W),9.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),7.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),12.U(4.W),3.U(4.W),0.U(4.W),1.U(4.W),0.U(4.W),3.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),1.U(4.W),1.U(4.W),10.U(4.W),4.U(4.W),1.U(4.W),4.U(4.W),10.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),6.U(4.W),3.U(4.W),0.U(4.W),10.U(4.W),10.U(4.W),0.U(4.W),10.U(4.W),10.U(4.W),0.U(4.W),3.U(4.W),6.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),3.U(4.W),9.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),9.U(4.W),3.U(4.W),0.U(4.W),6.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),7.U(4.W),2.U(4.W),0.U(4.W),6.U(4.W),9.U(4.W),6.U(4.W),9.U(4.W),6.U(4.W),0.U(4.W),2.U(4.W),7.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),3.U(4.W),0.U(4.W),9.U(4.W),3.U(4.W),12.U(4.W),3.U(4.W),9.U(4.W),0.U(4.W),3.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    )
    val imageR = VecInit(
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),2.U(4.W),2.U(4.W),2.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),3.U(4.W),7.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),4.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),9.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),7.U(4.W),2.U(4.W),0.U(4.W),2.U(4.W),7.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),1.U(4.W),0.U(4.W),7.U(4.W),7.U(4.W),0.U(4.W),7.U(4.W),7.U(4.W),0.U(4.W),1.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),1.U(4.W),3.U(4.W),1.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),1.U(4.W),3.U(4.W),1.U(4.W),0.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),3.U(4.W),2.U(4.W),3.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),3.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),7.U(4.W),1.U(4.W),4.U(4.W),1.U(4.W),7.U(4.W),0.U(4.W),2.U(4.W),2.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
        0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),0.U(4.W),
    )
    
    val image = List(imageR, imageB, imageG)

    val kernelConvolutionR = Module(new KernelConvolution(kernelSize, parallelPixels)).io
    val kernelConvolutionG = Module(new KernelConvolution(kernelSize, parallelPixels)).io
    val kernelConvolutionB = Module(new KernelConvolution(kernelSize, parallelPixels)).io
    val kernelConvolution  = List(kernelConvolutionR, kernelConvolutionG, kernelConvolutionB)
    
    val (kernelCounter, kernelCountReset) = Counter(true.B, kernelSize * kernelSize)

    for (j <- 0 until 3) {
      kernelConvolution(j).kernelVal_in := kernels(io.SPI_filterIndex)(kernelCounter)
    }
    
      val (imageCounterX, imageCounterXReset) = Counter(true.B, kernelSize)
      val (imageCounterY, imageCounterYReset) = Counter(imageCounterXReset, kernelSize)
      val pixelIndex = RegInit(UInt(32.W), 0.U)

      for (i <- 0 until parallelPixels){
          val x = (pixelIndex + i.U) % imageWidth.U + imageCounterX - 1.U
          val y = (pixelIndex + i.U) / imageWidth.U + imageCounterY - 1.U
          val greyScalePix = (image(0)(y * imageWidth.U + x) * 20.U + image(1)(y * imageWidth.U + x) * 70.U + image(2)(y * imageWidth.U + x) * 10.U) / 100.U

          for (j <- 0 until 3) {
            when(x < 0.U || x >= imageWidth.U || y < 0.U || y >= imageHeight.U){
                kernelConvolution(j).pixelVal_in(i) := 0.U
            }.elsewhen(greyScale) {
              kernelConvolution(j).pixelVal_in(i) := greyScalePix
            } .otherwise {
              kernelConvolution(j).pixelVal_in(i) := image(j)(y * imageWidth.U + x)
            }
          }
      }

      val normVal = kernelSums(io.SPI_filterIndex)
      val pixOut = List(RegInit(VecInit(List.fill(parallelPixels)(0.S(9.W)))), RegInit(VecInit(List.fill(parallelPixels)(0.S(9.W)))), RegInit(VecInit(List.fill(parallelPixels)(0.S(9.W))))) // added to try to fix timing
      val validOut = RegInit(Bool(), false.B)

      for (j <- 0 until 3) {
        for(i <- 0 until parallelPixels){
          pixOut(j)(i) := kernelConvolution(j).pixelVal_out(i) / normVal

          when (pixOut(j)(i) < 0.S && colorInvert) {  // normalize (clip hi/lo and scale rest)
            io.pixelVal_out(j)(i) := 15.U
          } .elsewhen(pixOut(j)(i) < 0.S) {
              io.pixelVal_out(j)(i) := 0.U
              // io.pixelVal_out(j)(i) := (~pixOut + 1.S).asUInt
          } .elsewhen(pixOut(j)(i) > 15.S && colorInvert) {
            io.pixelVal_out(j)(i) := 0.U
          } .elsewhen(pixOut(j)(i) > 15.S) {
            io.pixelVal_out(j)(i) := 15.U
          } .elsewhen(colorInvert) {
            io.pixelVal_out(j)(i) := 15.U - pixOut(j)(i).asUInt
          } .otherwise {
            io.pixelVal_out(j)(i) := pixOut(j)(i).asUInt
          }
        }
      }
      
    // io.valid_out := kernelConvolution(0).valid_out 
    validOut := kernelConvolution(0).valid_out 
    io.valid_out := validOut 
    
    when(kernelCountReset){
        pixelIndex := pixelIndex + parallelPixels.U
        when(pixelIndex === imageWidth.U * imageHeight.U){
            pixelIndex := 0.U
        }
    }
}

// main object for compilation 
object FilterDriver extends App {
  val imageWidth = 96 
  val imageHeight = 54 
  val parallelPixels = 8
  val kernelSize = 3
  chisel3.Driver.execute(args, () => new Filter(imageWidth, imageHeight, parallelPixels, kernelSize))
}
