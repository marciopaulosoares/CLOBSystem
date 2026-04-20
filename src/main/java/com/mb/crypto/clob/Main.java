package com.mb.crypto.clob;

import com.mb.crypto.clob.domain.Account;
import com.mb.crypto.clob.domain.AccountId;
import com.mb.crypto.clob.domain.Asset;
import com.mb.crypto.clob.domain.Instrument;

import java.math.BigDecimal;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        var aliceAccount = new Account(new AccountId("alice-22868867090"));
        var johnAccount = new Account(new AccountId("john-56601442097"));
        var mikeAccount = new Account(new AccountId("mike-34990104021"));

        ClobSystem clobSystem = new ClobSystem(
            List.of(new Instrument(Asset.BRL, Asset.BTC)),
            List.of(aliceAccount, johnAccount, mikeAccount)
        );

        clobSystem.deposit(aliceAccount.getId(), Asset.BRL, new BigDecimal(1000));
        clobSystem.deposit(johnAccount.getId(), Asset.BTC, new BigDecimal("0.5"));
    }
}