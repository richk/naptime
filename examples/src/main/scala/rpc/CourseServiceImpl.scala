package rpc

import javax.inject.Inject
import javax.inject.Singleton

import conversions.CourseConversions
import org.coursera.protobuf.CourseService
import org.coursera.protobuf.CourseService.CourseServiceGrpc
import org.coursera.protobuf.CourseService.FindCoursesRequest
import org.coursera.protobuf.ids.CourseId
import org.coursera.protobuf.pagination.ResponsePagination
import stores.CourseStore

import scala.concurrent.Future

@Singleton
class CourseServiceImpl @Inject()(courseStore: CourseStore)
    extends CourseServiceGrpc.CourseService {

  override def findCourses(request: FindCoursesRequest) =
    Future.successful {
      val courses = courseStore
        .all()
        .filter {
          case (id, course) =>
            (request.courseIds.isEmpty || request.courseIds.contains(
              CourseId(id))) &&
              (request.instructorIds.isEmpty || request.instructorIds
                .map(_.id)
                .intersect(course.instructorIds)
                .nonEmpty)
        }

      val coursesAfterNext = request.paging.start
        .map(start => courses.dropWhile { case (id, _) => id != start })
        .getOrElse(courses)

      val coursesSubset = coursesAfterNext.take(request.paging.limit)

      val next =
        coursesAfterNext.drop(request.paging.limit).headOption.map(_._1)

      CourseService.KeyedCourses(courses = coursesSubset.toList.map {
        case (id, course) => CourseConversions.courierToProto(id, course)
      }, ResponsePagination(next = next, total = Some(courses.size)))
    }

}