package dev.kotryos.minischeduler.scheduling.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface MeetingRepository extends Repository<Meeting, Long> {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(long id);

    @Query("select m from Meeting m where m.id in :ids order by m.startAt")
    List<Meeting> findAllByIds(@Param("ids") Collection<Long> ids);

    void delete(Meeting meeting);
}
