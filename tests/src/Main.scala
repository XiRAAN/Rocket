package org.chipsalliance.rocketchip.internal.tests

import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.stage.phases.{Convert, Elaborate}
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.system.ExampleRocketSystem
import org.chipsalliance.cde.config.{Config, Parameters}
import mainargs._

object Main {
  @main def elaborate(
    @arg(name = "dir", doc = "output directory") dir:                    String,
    @arg(name = "top", doc = "the top in ExampleRocketSystem type") top: String,
    @arg(name = "config", doc = "CDE configs") config:                   Seq[String]
  ) = {
    var topName: String = null
    val gen = () =>
      Class
        .forName(top)
        .getConstructor(classOf[Parameters])
        .newInstance(new Config(config.foldRight(Parameters.empty) {
          case (currentName, config) =>
            val currentConfig = Class.forName(currentName).newInstance.asInstanceOf[Config]
            currentConfig ++ config
        })) match {
        case lm: ExampleRocketSystem => lm
      }

    val annos = Seq(
      new Elaborate,
      new Convert
    ).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
        ChiselGeneratorAnnotation(() => new Testbench(gen()))
      ): AnnotationSeq
    ) { case (annos, phase) => phase.transform(annos) }
      .flatMap {
        case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
          topName = circuit.main
          os.write(os.Path(dir) / s"${circuit.main}.fir", circuit.serialize)
          None
        case _: chisel3.stage.ChiselCircuitAnnotation => None
        case _: chisel3.stage.DesignAnnotation[_]     => None
        case a => Some(a)
      }
    os.write(os.Path(dir) / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
    freechips.rocketchip.util.ElaborationArtefacts.files.foreach {
      case (ext, contents) => os.write.over(os.Path(dir) / s"${config.mkString("_")}.${ext}", contents())
    }
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
