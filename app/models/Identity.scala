package models

/**
 * Type class providing identity manipulation methods
 */
trait Identity[T, ID] {
  def of(entity: T): Option[ID]
  def set(entity: T, id: ID): T
  def clear(entity: T): T
  def next: ID
}