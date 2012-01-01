package org.scalatra

import ScalatraApp.{Action, MultiParams}
import util.MultiMap

/**
 * A route is a set of matchers and an action.  A route is considered to match
 * if all of its route matchers return Some.  If a route matches, its action
 * may be invoked.  The route parameters extracted by the matchers are made
 * available to the action.
 */
case class Route(
  routeMatchers: Seq[RouteMatcher] = Seq.empty,
  action: Action,
  contextPath: () => String = () => "",
  metadata: Map[Symbol, Any] = Map.empty
)
{
  /**
   * Optionally returns this route's action and the multi-map of route
   * parameters extracted from the matchers.  Each matcher's returned params
   * are merged into those of the previous.  If any matcher returns None,
   * None is returned.  If there are no route matchers, some empty map is
   * returned.
   */
  def apply(): Option[MatchedRoute] = {
    routeParams map (MatchedRoute(action, _))
  }

  private def initialParams = None.asInstanceOf[Option[MultiParams]] 
  private def routeParams = (initialParams /: routeMatchers) { (acc, matchRoute) => (acc, matchRoute()) match {
    case (None, None) => None
    case (None, Some(mm: MultiParams)) => Some(mm)
    case (r, None) => r
    case (Some(p), Some(mm: MultiParams)) => Some(p ++ mm)
  } }

  /**
   * The reversible matcher of a route is the first reversible matcher, if
   * any.  This matcher may be used to generate URIs.
   */
  lazy val reversibleMatcher: Option[RouteMatcher] =
    routeMatchers find (_.isInstanceOf[ReversibleRouteMatcher])

  /**
   * Determines whether this is a reversible route.
   */
  lazy val isReversible: Boolean = !reversibleMatcher.isEmpty

  override def toString: String = routeMatchers mkString " "
}

object Route {
  def apply(transformers: Seq[RouteTransformer], action: Action): Route =
    apply(transformers, action, () => "")

  def apply(transformers: Seq[RouteTransformer], action: Action, contextPath: () => String): Route = {
    val route = Route(action = action, contextPath = contextPath)
    transformers.foldRight(route) { _ apply _ }
  }

  def appendMatcher(matcher: RouteMatcher): RouteTransformer = { route =>
    route.copy(routeMatchers = route.routeMatchers :+ matcher)
  }
}

/**
 * An action and the multi-map of route parameters to invoke it with.
 */
case class MatchedRoute(action: Action, multiParams: MultiParams)