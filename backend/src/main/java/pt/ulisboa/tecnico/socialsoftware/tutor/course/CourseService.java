package pt.ulisboa.tecnico.socialsoftware.tutor.course;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pt.ulisboa.tecnico.socialsoftware.tutor.config.Demo;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.domain.Course;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.domain.CourseExecution;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.dto.CourseDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.repository.CourseExecutionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.repository.CourseRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.TutorException;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.User;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.UserRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.dto.ExternalUserDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.dto.StudentDto;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.*;

@Service
public class CourseService {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseExecutionRepository courseExecutionRepository;

    @Autowired
    private UserRepository userRepository;


    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto getCourseExecutionById(int courseExecutionId) {
        return courseExecutionRepository.findById(courseExecutionId)
                .map(CourseDto::new)
                .orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND, courseExecutionId));
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<CourseDto> getCourseExecutions(User.Role role) {
        return courseExecutionRepository.findAll().stream()
                .filter(courseExecution -> role.equals(User.Role.ADMIN) ||
                        (role.equals(User.Role.DEMO_ADMIN) && courseExecution.getCourse().getName().equals(Demo.COURSE_NAME)))
                .map(CourseDto::new)
                .sorted(Comparator
                        .comparing(CourseDto::getName)
                        .thenComparing(CourseDto::getAcademicTerm))
                .collect(Collectors.toList());
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto createTecnicoCourseExecution(CourseDto courseDto) {
        courseDto.setCourseExecutionType(Course.Type.TECNICO);
        courseDto.setCourseType(Course.Type.TECNICO);

        Course course = getCourse(courseDto.getName(), Course.Type.TECNICO);

        CourseExecution courseExecution = course.getCourseExecution(courseDto.getAcronym(), courseDto.getAcademicTerm(), courseDto.getCourseExecutionType())
                .orElseGet(() -> createCourseExecution(course, courseDto));

        courseExecution.setStatus(CourseExecution.Status.ACTIVE);
        return new CourseDto(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto createExternalCourseExecution(CourseDto courseDto) {
        courseDto.setCourseExecutionType(Course.Type.EXTERNAL);

        Course course = getCourse(courseDto.getName(), courseDto.getCourseType());

        CourseExecution courseExecution = createCourseExecution(course, courseDto);

        courseExecution.setStatus(CourseExecution.Status.ACTIVE);
        return new CourseDto(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void removeCourseExecution(int courseExecutionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(courseExecutionId)
                .orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND, courseExecutionId));

        courseExecution.remove();

        courseExecutionRepository.delete(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deactivateCourseExecution(int executionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(executionId).orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND));
        courseExecution.setStatus(CourseExecution.Status.INACTIVE);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<StudentDto> getCourseStudents(int executionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(executionId).orElse(null);
        if (courseExecution == null) {
            return new ArrayList<>();
        }
        return courseExecution.getUsers().stream()
                .filter(user -> user.getRole().equals(User.Role.STUDENT))
                .sorted(Comparator.comparing(User::getKey))
                .map(StudentDto::new)
                .collect(Collectors.toList());
    }

    private Course getCourse(String name, Course.Type type) {
        if (type == null)
            throw new TutorException(INVALID_TYPE_FOR_COURSE);

        return courseRepository.findByNameType(name, type.toString())
                .orElseGet(() -> courseRepository.save(new Course(name, type)));
    }

    private CourseExecution createCourseExecution(Course existingCourse, CourseDto courseDto) {
        CourseExecution courseExecution = new CourseExecution(existingCourse, courseDto.getAcronym(), courseDto.getAcademicTerm(), courseDto.getCourseExecutionType());
        courseExecutionRepository.save(courseExecution);
        return courseExecution;
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto getDemoCourse() {
        CourseExecution courseExecution =  this.courseExecutionRepository.findByFields(Demo.COURSE_ACRONYM, Demo.COURSE_ACADEMIC_TERM, Course.Type.TECNICO.toString()).orElse(null);

        if (courseExecution == null) {
            return createTecnicoCourseExecution(new CourseDto(Demo.COURSE_NAME, Demo.COURSE_ACRONYM, Demo.COURSE_ACADEMIC_TERM));
        }
        return new CourseDto(courseExecution);
    }

    public CourseExecution getDemoCourseExecution() {
        return this.courseExecutionRepository.findByFields(Demo.COURSE_ACRONYM, Demo.COURSE_ACADEMIC_TERM, Course.Type.TECNICO.toString()).orElseGet(() -> {
            Course course = getCourse(Demo.COURSE_NAME, Course.Type.TECNICO);
            CourseExecution courseExecution = new CourseExecution(course, Demo.COURSE_ACRONYM, Demo.COURSE_ACADEMIC_TERM, Course.Type.TECNICO);
            courseExecution.setStatus(CourseExecution.Status.ACTIVE);
            return courseExecutionRepository.save(courseExecution);
        });
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<ExternalUserDto> getExternalUsers(Integer courseExecutionId){
        CourseExecution execution = getExternalCourseExecution(courseExecutionId);
        return execution.getStudents().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(ExternalUserDto::new)
                .collect(Collectors.toList());
    }

    private CourseExecution getExternalCourseExecution(Integer courseExecutionId) {
        CourseExecution execution = courseExecutionRepository
                .findById(courseExecutionId)
                .orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND, courseExecutionId));
        checkExternalExecution(execution);
        return execution;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CourseDto deleteExternalInactiveUsers(Integer courseExecutionId, List<Integer> usersId){
        CourseExecution courseExecution = getExternalCourseExecution(courseExecutionId);
        deleteUsersOfUserIds(usersId, courseExecution);
        return new CourseDto(courseExecution);
    }

    private void deleteUsersOfUserIds(List<Integer> usersId, CourseExecution courseExecution) {
        usersId = getExecutionFilteredIds(usersId, courseExecution);
        deleteUsers(usersId);
    }

    private void deleteUsers(List<Integer> usersId) {
        for (Integer id : usersId) {
            User user = userRepository
                    .findById(id)
                    .orElseThrow(() -> new TutorException((USER_NOT_FOUND)));
            user.remove();
            userRepository.delete(user);
        }
    }

    private List<Integer> getExecutionFilteredIds(List<Integer> usersId, CourseExecution courseExecution) {
        List<Integer> executionUserIdList = courseExecution.getUsers().stream()
                .map(User::getId)
                .collect(Collectors.toList());
        return usersId.stream()
                .filter(executionUserIdList::contains)
                .collect(Collectors.toList());
    }

    private void checkExternalExecution(CourseExecution courseExecution) {
        if (!courseExecution.getType().equals(Course.Type.EXTERNAL)) {
            throw new TutorException(COURSE_EXECUTION_NOT_EXTERNAL);
        }
    }
}
