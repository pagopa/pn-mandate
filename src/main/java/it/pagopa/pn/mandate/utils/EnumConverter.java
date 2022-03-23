package it.pagopa.pn.mandate.utils;

import java.util.NoSuchElementException;

public interface EnumConverter<ID, T extends Enum<T>> {

  public static
  <ID, T extends Enum<T>&EnumConverter<ID,T>> T fromId(ID id, Class<T> type) {
       if (id == null) {
           return null;
       }
       for (T en : type.getEnumConstants()) {
           if (en.getId().equals(id)) {
               return en;
           }
       }
       throw new NoSuchElementException();
   }

   ID getId();

   String getDescriptionKey();
}