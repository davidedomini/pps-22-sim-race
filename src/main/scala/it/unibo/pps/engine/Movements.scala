package it.unibo.pps.engine

import alice.tuprolog.{Term, Theory}
import it.unibo.pps.prolog.Scala2P
import it.unibo.pps.model.{RenderParams, RenderStraightParams, RenderTurnParams}
import it.unibo.pps.utility.monadic.io
import monix.eval.Task
import it.unibo.pps.utility.PimpScala.RichTuple2.*
import it.unibo.pps.utility.PimpScala.RichInt.*
import it.unibo.pps.utility.GivenConversion.DirectionGivenConversion.given_Conversion_Direction_Int
import it.unibo.pps.model.factor.CarFactors
import scala.Tuple2 as Point2D
import it.unibo.pps.engine.SimulationConstants.*
import it.unibo.pps.model.car.Car
import it.unibo.pps.model.track.Phase
import it.unibo.pps.model.track.Direction

object Converter:

  /** Converts velocity from km/h to m/s */
  def kmh2ms(vel: Double): Double = vel / 3.6

  /** Converts velocity from m/s to km/h */
  def ms2kmh(vel: Double): Double = vel * 3.6

trait Movements:

  /** Computes new straight velocity
    * @param car
    *   The car to be updated to
    * @param time
    *   Virtual time
    * @param phase
    *   Represents the different phases of the sector
    */
  def updateVelocityOnStraight(car: Car, time: Int, phase: Phase): Task[Int]

  /** Computes new turn velocity
    * @param car
    *   The car to be updated to
    */
  def updateVelocityOnTurn(car: Car): Task[Int]

  /** Computes the new position in the straight when the phase is acceleration
    * @param car
    *   The car to be updated to
    * @param time
    *   Virtual time
    */
  def updatePositionOnStraightAcceleration(car: Car, time: Int): Task[Point2D[Int, Int]]

  /** Computes the new position in the straight when the phase is deceleration
    * @param car
    *   The car to be updated to
    * @param time
    *   Virtual time
    */
  def updatePositionOnStraightDeceleration(car: Car, time: Int): Task[Point2D[Int, Int]]

  /** Computes the new turn position
    * @param car
    *   The car to be updated to
    * @param time
    *   Virtual time
    */
  def updatePositionOnTurn(car: Car, time: Int, velocity: Double, d: RenderParams): Task[Point2D[Int, Int]]

object Movements:
  def apply(): Movements = new MovementsImpl()

  private class MovementsImpl() extends Movements:

    override def updateVelocityOnStraight(car: Car, time: Int, phase: Phase): Task[Int] = phase match
      case Phase.Acceleration => updateVelocityStraightAcceleration(car, time)
      case Phase.Deceleration => updateVelocityStraightDeceleration(car, time)
      case Phase.Ended => io(car.actualSpeed)

    override def updateVelocityOnTurn(car: Car): Task[Int] =
      io((car.actualSpeed * (0.94 + (car.driver.skills / 100))).toInt)

    override def updatePositionOnStraightAcceleration(car: Car, time: Int): Task[Point2D[Int, Int]] =
      for
        x <- io(car.renderCarParams.position._1)
        direction <- io(car.actualSector.direction)
        velocity <- io(car.actualSpeed)
        acceleration <- io(car.acceleration)
        newP <- newPositionStraight(x, velocity, time, acceleration, direction)
      yield (newP, car.renderCarParams.position._2)

    override def updatePositionOnStraightDeceleration(car: Car, time: Int): Task[Point2D[Int, Int]] =
      for
        x <- io(car.renderCarParams.position._1)
        direction <- io(car.actualSector.direction)
        newP <- newPositionStraight(x, car.actualSpeed, time, 1, direction)
      yield (newP, car.renderCarParams.position._2)

    override def updatePositionOnTurn(car: Car, time: Int, velocity: Double, d: RenderParams): Task[Point2D[Int, Int]] =
      d match
        case RenderTurnParams(center, pExternal, pInternal, _, _, endX, _, _) =>
          for
            x <- io(car.renderCarParams.position._1)
            teta_t <- io(0.5 * car.acceleration * (time ** 2))
            direction <- io(car.actualSector.direction)
            r <- io(car.renderCarParams.position euclideanDistance center)
            turnRadiusExternal <- io(center euclideanDistance pExternal)
            turnRadiusInternal <- io(center euclideanDistance pInternal)
            alpha <- io(direction match
              case Direction.Forward => 0
              case Direction.Backward => 180
            )
            newX <- io((center._1 + (r * Math.sin(Math.toRadians(teta_t + alpha)))).toInt)
            newY <- io((center._2 - (r * Math.cos(Math.toRadians(teta_t + alpha)))).toInt)
            np <- io(checkTurnBounds((newX, newY), center, turnRadiusExternal, turnRadiusInternal, direction))
            position <- io(checkEnd(np, endX, direction))
          yield position

    private def newPositionStraight(
        x: Int,
        velocity: Double,
        time: Int,
        acceleration: Double,
        direction: Int
    ): Task[Int] =
      for
        v <- io(Converter.kmh2ms(velocity))
        vel <- io((x + ((v * time + 0.5 * acceleration * (time ** 2)) / 160) * direction).toInt)
      yield vel

    private def updateVelocityStraightAcceleration(car: Car, time: Int): Task[Int] =
      for
        vel <- io(Converter.kmh2ms(car.actualSpeed))
        v <- io((vel + car.acceleration * time).toInt)
        v <- io(Converter.ms2kmh(v).toInt)
        v <- io(if v > car.maxSpeed then car.maxSpeed else v)
        d <- io(CarFactors.totalDamage(v, car.fuel, (car.tyre, car.actualLap), car.degradation))
        v <- io(v - d)
      yield v

    private def updateVelocityStraightDeceleration(car: Car, time: Int): Task[Int] =
      io((car.actualSpeed * STRAIGHT_VELOCITY_REDUCTION_FACTOR).toInt)

    private def checkTurnBounds(
        p: Point2D[Int, Int],
        center: Point2D[Int, Int],
        rExternal: Int,
        rInternal: Int,
        direction: Int
    ): Point2D[Int, Int] =
      var dx = (p._1 + 12, p._2) euclideanDistance center
      var dy = (p._1, p._2 + 12) euclideanDistance center
      if dx - rExternal < 0 && direction == 1 then dx = rExternal
      if dy - rExternal < 0 && direction == 1 then dy = rExternal
      if (dx >= rExternal || dy >= rExternal) && direction == 1 then (p._1 - (dx - rExternal), p._2 - (dy - rExternal))
      else if (dx <= rInternal || dy <= rInternal) && direction == -1 then
        (p._1 + (dx - rInternal), p._2 + (dy - rInternal))
      else p

    private def checkEnd(p: Point2D[Int, Int], end: Int, direction: Int): Point2D[Int, Int] =
      if direction == 1 then if p._1 < end then (end - 1, p._2) else p
      else if p._1 > end then (end + 1, p._2)
      else p
