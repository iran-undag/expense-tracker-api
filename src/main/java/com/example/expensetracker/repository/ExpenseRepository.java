package com.example.expensetracker.repository;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.dto.CategoryTotalDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {
    List<Expense> findByUserid(String userid);
    List<Expense> findByUseridIn(List<String> userids);
    List<Expense> findByUseridOrderByDateAscIdAsc(String userid);
    List<Expense> findByUseridInOrderByDateAscIdAsc(List<String> userids);
    List<Expense> findByUseridAndDateBetweenOrderByDateAscIdAsc(String userid, LocalDate startDate, LocalDate endDate);
    List<Expense> findByUseridInAndDateBetweenOrderByDateAscIdAsc(
        List<String> userids, LocalDate startDate, LocalDate endDate);
    Page<Expense> findByUserid(String userid, Pageable pageable);
    Page<Expense> findByUseridIn(List<String> userids, Pageable pageable);
    List<Expense> findByUseridAndDate(String userid, LocalDate date);
    List<Expense> findByUseridInAndDate(List<String> userids, LocalDate date);
    List<Expense> findByUseridAndDateBetween(String userid, LocalDate startDate, LocalDate endDate);
    List<Expense> findByUseridInAndDateBetween(List<String> userids, LocalDate startDate, LocalDate endDate);
    Page<Expense> findByUseridAndDateBetween(String userid, LocalDate startDate, LocalDate endDate, Pageable pageable);
    Page<Expense> findByUseridInAndDateBetween(
        List<String> userids, LocalDate startDate, LocalDate endDate, Pageable pageable);
    Optional<Expense> findByIdAndUserid(Long id, String userid);
    Optional<Expense> findByIdAndUseridIn(Long id, List<String> userids);

    @Query("""
        select new com.example.expensetracker.dto.CategoryTotalDto(
            coalesce(e.category, 'Other'),
            coalesce(sum(e.amount), 0)
        )
        from Expense e
        where e.userid = :userId and e.date between :startDate and :endDate
        group by coalesce(e.category, 'Other')
        """)
    List<CategoryTotalDto> sumByCategoryForUserAndDateBetween(String userId, LocalDate startDate, LocalDate endDate);

    @Query("""
        select new com.example.expensetracker.dto.CategoryTotalDto(
            coalesce(e.category, 'Other'),
            coalesce(sum(e.amount), 0)
        )
        from Expense e
        where e.userid in :userIds and e.date between :startDate and :endDate
        group by coalesce(e.category, 'Other')
        """)
    List<CategoryTotalDto> sumByCategoryForOwnersAndDateBetween(
        List<String> userIds, LocalDate startDate, LocalDate endDate);
}
