package models

/**
 * Type class providing identity manipulation methods
 */
trait Identity[E, ID] {
  def name: String
  def of(entity: E): Option[ID]
  def set(entity: E, id: ID): E
  def clear(entity: E): E
  def next: ID
}