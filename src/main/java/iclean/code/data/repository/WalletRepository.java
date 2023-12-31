package iclean.code.data.repository;

import iclean.code.data.domain.Wallet;
import iclean.code.data.enumjava.WalletTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    @Query("SELECT wallet FROM Wallet wallet WHERE wallet.user.userId = ?1 " +
            "AND wallet.walletTypeEnum = ?2")
    Wallet getWalletByUserIdAndType(Integer userId, WalletTypeEnum walletTypeEnum);
}
